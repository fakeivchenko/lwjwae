package dev.ivchenko.lwjwae.internal;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.download.DownloadAction;
import dev.ivchenko.lwjwae.download.DownloadDecision;
import dev.ivchenko.lwjwae.download.DownloadRequest;
import dev.ivchenko.lwjwae.download.DownloadState;
import dev.ivchenko.lwjwae.download.Downloads;
import dev.ivchenko.lwjwae.event.DownloadEvent;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.util.JsonUtil;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The downloads of one window: who decides where they go, and who hears how they get on.
 *
 * <p>A backend reports what its engine does, and this class makes it the same on every platform.
 * The handler runs on a virtual thread of its own, so it can ask the user, read a setting, or show
 * a dialog, while the engine waits for the answer in the way that it has: a deferral on Windows, a
 * completion handler on macOS, and the main loop of GTK, which keeps running. Without a handler,
 * the file goes to the downloads folder of the user, under a name that no file there has, as a
 * browser does.
 *
 * <p>The engines report progress as often as bytes arrive, which can be thousands of times a
 * second. Only the first report of each tenth of a second becomes an event, and the last state goes
 * with the event that ends the download. The listeners run on a virtual thread of the window, one
 * event after the other, so that none sees a download end before it started.
 *
 * <p>A file in the downloads folder is claimed when the download is decided: it's created empty,
 * and the name stays taken in this process until the download ends, even where the engine needs to
 * find no file there and the backend removes it first, as WebKit on macOS does. A download that
 * doesn't complete, or never starts because its window closed, takes its claimed file with it, so
 * no empty file stays behind. A file that the handler names belongs to the application and stays.
 */
public final class WindowDownloads implements Downloads {
  private static final System.Logger LOG = System.getLogger(WindowDownloads.class.getName());

  /** The shortest time between two {@link DownloadState#PROGRESSED} events of a download. */
  private static final long PROGRESS_INTERVAL_NANOS = 100_000_000L;

  /**
   * The longest name of a file, in bytes of UTF-8, short of the 255 that the file systems of Linux
   * and macOS take, with room for the number that {@link #unique} adds.
   */
  private static final int NAME_BYTES = 200;

  /** The names that Windows keeps for devices, whatever their extension. */
  private static final Set<String> WINDOWS_DEVICES =
      Set.of(
          "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7",
          "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

  /** The files of the downloads folder that this process claimed and whose download goes on. */
  private static final Set<Path> CLAIMED = ConcurrentHashMap.newKeySet();

  private final Window window;
  private final Executor uiThread;
  private final Consumer<String> page;
  private final Function<String, CompletableFuture<Optional<Path>>> ask;
  private final Listeners<DownloadEvent> listeners = new Listeners<>("lwjwae-downloads");
  private final Map<Long, ActiveDownload> active = new ConcurrentHashMap<>();
  private final AtomicLong downloadIds = new AtomicLong();
  private final Supplier<Path> downloadsDirectory;

  /** The claimed files of this window whose download is decided and hasn't started yet. */
  private final Set<Path> unstarted = ConcurrentHashMap.newKeySet();

  private volatile Function<DownloadRequest, DownloadDecision> handler;
  private volatile boolean shut;

  /**
   * Creates the downloads of {@code window}.
   *
   * @param window The window whose page downloads.
   * @param uiThread Where a cancel goes to the engine.
   * @param page Where the events go for the page, as JSON.
   * @param ask Shows the dialog that saves a file, with the name that it proposes, for {@link
   *     DownloadAction#ASK}.
   */
  public WindowDownloads(
      Window window,
      Executor uiThread,
      Consumer<String> page,
      Function<String, CompletableFuture<Optional<Path>>> ask) {
    this(window, uiThread, page, ask, WindowDownloads::downloadsDirectory);
  }

  /** The same, with the folder that a download goes to without a handler. */
  WindowDownloads(
      Window window,
      Executor uiThread,
      Consumer<String> page,
      Function<String, CompletableFuture<Optional<Path>>> ask,
      Supplier<Path> downloadsDirectory) {
    this.window = window;
    this.uiThread = uiThread;
    this.page = page;
    this.ask = ask;
    this.downloadsDirectory = downloadsDirectory;
  }

  @Override
  public void handler(Function<DownloadRequest, DownloadDecision> handler) {
    this.handler = handler;
  }

  @Override
  public EventSubscription listen(Consumer<DownloadEvent> listener) {
    return this.listeners.listen(listener);
  }

  /**
   * Decides where the download of {@code request} goes, on a virtual thread.
   *
   * @return The file to write, or empty if nothing is downloaded. Never fails: a handler that
   *     throws or answers {@code null} downloads nothing.
   */
  public CompletableFuture<Optional<Path>> request(DownloadRequest request) {
    CompletableFuture<Optional<Path>> result = new CompletableFuture<>();
    Thread.ofVirtual()
        .name("lwjwae-download-decision")
        .start(
            () -> {
              try {
                Optional<Path> path = this.decide(request);
                if (this.shut && path.isPresent() && this.unstarted.remove(path.get())) {
                  // The window closed while the handler decided: nothing will start.
                  WindowDownloads.release(path.get(), true);
                  path = Optional.empty();
                }
                Optional<Path> decided = path;
                LOG.log(
                    System.Logger.Level.DEBUG,
                    () ->
                        "The download of "
                            + request.url()
                            + decided.map(file -> " goes to " + file).orElse(" is denied"));
                result.complete(path);
              } catch (Throwable t) {
                ThrowableUtil.report(t);
                result.complete(Optional.empty());
              }
            });
    return result;
  }

  /**
   * Records that a download started writing to {@code path}, and tells the listeners.
   *
   * @param cancel Cancels the download in the engine, on the UI thread.
   * @return The ID of the download, for the reports that follow.
   */
  public long started(URI url, Path path, long totalBytes, Runnable cancel) {
    long id = this.downloadIds.incrementAndGet();
    this.unstarted.remove(path);
    ActiveDownload download =
        new ActiveDownload(id, url, path, cancel, Math.max(-1, totalBytes), System.nanoTime());
    this.active.put(id, download);
    this.emit(download, DownloadState.STARTED, "");
    return id;
  }

  /** Records that {@code receivedBytes} of the download arrived, out of {@code totalBytes}. */
  public void progressed(long id, long receivedBytes, long totalBytes) {
    ActiveDownload download = this.active.get(id);
    if (download == null) {
      return;
    }
    download.receivedBytes = receivedBytes;
    if (totalBytes >= 0) {
      download.totalBytes = totalBytes;
    }
    long now = System.nanoTime();
    if (now - download.reportedAt >= PROGRESS_INTERVAL_NANOS) {
      download.reportedAt = now;
      this.emit(download, DownloadState.PROGRESSED, "");
    }
  }

  /**
   * Records that the download ended, and tells the listeners. A download that ended already, or
   * that this class doesn't know, is ignored.
   *
   * @param state One of the final states.
   * @param failure What went wrong, for {@link DownloadState#FAILED}.
   */
  public void ended(long id, DownloadState state, String failure) {
    ActiveDownload download = this.active.remove(id);
    if (download == null) {
      return;
    }
    WindowDownloads.release(download.path, state != DownloadState.COMPLETED);
    if (state == DownloadState.COMPLETED) {
      try {
        download.receivedBytes = Files.size(download.path);
        download.totalBytes = download.receivedBytes;
      } catch (IOException _) {
        // The engine said it's done; the last count it reported stands.
      }
    }
    this.emit(download, state, failure);
  }

  @Override
  public boolean cancel(long id) {
    ActiveDownload download = this.active.get(id);
    if (download == null) {
      return false;
    }
    this.uiThread.execute(download.cancel);
    return true;
  }

  /**
   * Stops the delivery: the window is gone. A claimed file whose download never started goes; one
   * whose download goes on belongs to the engine until the download ends.
   */
  public void shutdown() {
    this.shut = true;
    for (Path path : List.copyOf(this.unstarted)) {
      if (this.unstarted.remove(path)) {
        WindowDownloads.release(path, true);
      }
    }
    this.active.clear();
    this.listeners.shutdown();
  }

  private Optional<Path> decide(DownloadRequest request) throws IOException {
    Function<DownloadRequest, DownloadDecision> current = this.handler;
    if (current == null) {
      Path claimed =
          WindowDownloads.unique(this.downloadsDirectory.get(), request.suggestedFileName());
      this.unstarted.add(claimed);
      return Optional.of(claimed);
    }
    DownloadDecision decision = current.apply(request);
    if (decision == null) {
      return Optional.empty();
    }
    return switch (decision.action()) {
      case SAVE -> {
        Path parent = decision.path().getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        yield Optional.of(decision.path());
      }
      case ASK -> this.ask.apply(WindowDownloads.fileName(request.suggestedFileName())).join();
      case DENY -> Optional.empty();
    };
  }

  private void emit(ActiveDownload download, DownloadState state, String failure) {
    DownloadEvent event =
        new DownloadEvent(
            this.window,
            download.id,
            download.url,
            download.path,
            state,
            download.receivedBytes,
            download.totalBytes,
            failure);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", event.id());
    payload.put("url", event.url().toString());
    payload.put("path", event.path().toString());
    payload.put("state", state.pageName());
    payload.put("receivedBytes", event.receivedBytes());
    payload.put("totalBytes", event.totalBytes());
    payload.put("failure", event.failure());
    this.page.accept(JsonUtil.write(payload));
    this.listeners.deliver(event);
  }

  /**
   * The downloads folder of the user: {@code XDG_DOWNLOAD_DIR} of {@code user-dirs.dirs} on Linux,
   * and {@code Downloads} in the home folder elsewhere and without one, or the home folder itself
   * where that folder isn't there.
   */
  private static Path downloadsDirectory() {
    Path home = Path.of(System.getProperty("user.home"));
    Path directory = home.resolve("Downloads");
    if (!PlatformUtil.isWindows() && !PlatformUtil.isMacOs()) {
      directory = WindowDownloads.xdgDownloadsDirectory(home).orElse(directory);
    }
    return Files.isDirectory(directory) ? directory : home;
  }

  /**
   * {@code XDG_DOWNLOAD_DIR} of {@code user-dirs.dirs}, which the desktop writes in its language.
   */
  private static Optional<Path> xdgDownloadsDirectory(Path home) {
    String config = System.getenv("XDG_CONFIG_HOME");
    Path file =
        (config == null || config.isBlank() ? home.resolve(".config") : Path.of(config))
            .resolve("user-dirs.dirs");
    try {
      for (String line : Files.readAllLines(file)) {
        String trimmed = line.strip();
        if (trimmed.startsWith("XDG_DOWNLOAD_DIR=")) {
          String value = trimmed.substring("XDG_DOWNLOAD_DIR=".length()).replace("\"", "");
          return Optional.of(Path.of(value.replace("$HOME", home.toString())));
        }
      }
    } catch (IOException | RuntimeException _) {
      // No such file, or one that isn't readable: the default folder stands.
    }
    return Optional.empty();
  }

  /**
   * A name for a file of {@code suggested}, which a server sends and which may be anything: the
   * last part of it as a path, with every character that a file system of Windows, macOS, or Linux
   * refuses replaced by {@code _}, without the dots and spaces that Windows drops from the end, and
   * short enough for every file system, the extension kept.
   *
   * <p>APFS refuses what isn't a character of Unicode: a code point that no version assigned, and a
   * half of a surrogate pair without the other half. Both become {@code _} too.
   */
  static String fileName(String suggested) {
    String name = suggested.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    StringBuilder safe = new StringBuilder(name.length());
    name.codePoints().forEach(c -> safe.appendCodePoint(WindowDownloads.isRefused(c) ? '_' : c));
    name = safe.toString().strip().replaceAll("[. ]+$", "");
    if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
      return "download";
    }
    if (WINDOWS_DEVICES.contains(name.split("\\.", 2)[0].toUpperCase(Locale.ROOT))) {
      name = "_" + name;
    }
    return WindowDownloads.shortened(name);
  }

  /** Whether a file system of Windows, macOS, or Linux refuses {@code c} in a file name. */
  private static boolean isRefused(int c) {
    int type = Character.getType(c);
    return c < 0x20
        || c == 0x7f
        || "<>:\"|?*".indexOf(c) >= 0
        || type == Character.UNASSIGNED
        || type == Character.SURROGATE;
  }

  /**
   * {@code name} cut to {@link #NAME_BYTES} bytes of UTF-8, at a whole character, with its
   * extension kept where it's short.
   */
  private static String shortened(String name) {
    if (name.getBytes(StandardCharsets.UTF_8).length <= NAME_BYTES) {
      return name;
    }
    int dot = name.lastIndexOf('.');
    String extension = dot > 0 && name.length() - dot <= 16 ? name.substring(dot) : "";
    String stem = extension.isEmpty() ? name : name.substring(0, dot);
    int budget = NAME_BYTES - extension.getBytes(StandardCharsets.UTF_8).length;
    StringBuilder cut = new StringBuilder();
    int used = 0;
    for (int i = 0; i < stem.length(); ) {
      int c = stem.codePointAt(i);
      int bytes = new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8).length;
      if (used + bytes > budget) {
        break;
      }
      cut.appendCodePoint(c);
      used += bytes;
      i += Character.charCount(c);
    }
    return cut + extension;
  }

  /**
   * Claims a file in {@code directory} for a download named {@code suggested}, with a number added
   * to the name where a file of that name is there, as {@code report (1).pdf}, or where this
   * process claimed the name for a download that goes on. The file is created empty, which claims
   * the name against other processes too.
   *
   * @throws IOException If the file can't be created.
   */
  static Path unique(Path directory, String suggested) throws IOException {
    String name = WindowDownloads.fileName(suggested);
    Path folder = directory.toAbsolutePath().normalize();
    Path path = folder.resolve(name);
    int dot = name.lastIndexOf('.');
    String stem = dot > 0 ? name.substring(0, dot) : name;
    String extension = dot > 0 ? name.substring(dot) : "";
    for (int number = 1; ; number++) {
      if (CLAIMED.add(path)) {
        try {
          return Files.createFile(path);
        } catch (FileAlreadyExistsException _) {
          CLAIMED.remove(path);
        } catch (IOException | RuntimeException e) {
          CLAIMED.remove(path);
          throw e;
        }
      }
      path = folder.resolve(stem + " (" + number + ")" + extension);
    }
  }

  /**
   * Gives up the claim of this process on {@code path}, if it has one, and removes the file if
   * {@code discard}: the download that it was claimed for didn't complete, so nothing in it is
   * worth keeping. A file that this process didn't claim is never touched.
   */
  static void release(Path path, boolean discard) {
    if (!CLAIMED.remove(path.toAbsolutePath().normalize()) || !discard) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      ThrowableUtil.report(e);
    }
  }
}
