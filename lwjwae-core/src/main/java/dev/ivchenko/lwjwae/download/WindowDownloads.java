package dev.ivchenko.lwjwae.download;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.event.DownloadEvent;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.util.JsonUtil;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

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
 */
public final class WindowDownloads {
  /** The shortest time between two {@link DownloadState#PROGRESSED} events of a download. */
  private static final long PROGRESS_INTERVAL_NANOS = 100_000_000L;

  private final Window window;
  private final Consumer<String> page;
  private final Function<String, CompletableFuture<Optional<Path>>> ask;
  private final Map<Long, Consumer<DownloadEvent>> listeners = new ConcurrentHashMap<>();
  private final Map<Long, ActiveDownload> active = new ConcurrentHashMap<>();
  private final AtomicLong listenerIds = new AtomicLong();
  private final AtomicLong downloadIds = new AtomicLong();
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(Thread.ofVirtual().name("lwjwae-downloads").factory());

  private volatile Function<DownloadRequest, DownloadDecision> handler;

  /**
   * Creates the downloads of {@code window}.
   *
   * @param window The window whose page downloads.
   * @param page Where the events go for the page, as JSON.
   * @param ask Shows the dialog that saves a file, with the name that it proposes, for {@link
   *     DownloadAction#ASK}.
   */
  public WindowDownloads(
      Window window,
      Consumer<String> page,
      Function<String, CompletableFuture<Optional<Path>>> ask) {
    this.window = window;
    this.page = page;
    this.ask = ask;
  }

  /** Sets the handler that decides where a download goes; {@code null} brings the default back. */
  public void handler(Function<DownloadRequest, DownloadDecision> handler) {
    this.handler = handler;
  }

  /** Registers {@code listener} for every event from now on. */
  public EventSubscription listen(Consumer<DownloadEvent> listener) {
    long id = this.listenerIds.incrementAndGet();
    this.listeners.put(id, listener);
    return () -> this.listeners.remove(id);
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
                result.complete(this.decide(request));
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

  /**
   * Cancels the download {@code id} on {@code uiThread}.
   *
   * @return Whether the download was still going.
   */
  public boolean cancel(long id, Executor uiThread) {
    ActiveDownload download = this.active.get(id);
    if (download == null) {
      return false;
    }
    uiThread.execute(download.cancel);
    return true;
  }

  /** Stops the delivery: the window is gone. */
  public void shutdown() {
    this.active.clear();
    this.executor.shutdown();
  }

  /**
   * A file in the downloads folder of the user for a download named {@code suggestedFileName}, with
   * a number added to the name where a file of that name is there, as {@code report (1).pdf}.
   */
  public static Path defaultPath(String suggestedFileName) {
    return WindowDownloads.unique(WindowDownloads.downloadsDirectory(), suggestedFileName);
  }

  private Optional<Path> decide(DownloadRequest request) throws IOException {
    Function<DownloadRequest, DownloadDecision> current = this.handler;
    DownloadDecision decision =
        current == null
            ? DownloadDecision.saveTo(WindowDownloads.defaultPath(request.suggestedFileName()))
            : current.apply(request);
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
    if (this.listeners.isEmpty() || this.executor.isShutdown()) {
      return;
    }
    List<Consumer<DownloadEvent>> current = List.copyOf(this.listeners.values());
    try {
      this.executor.execute(
          () -> {
            for (Consumer<DownloadEvent> listener : current) {
              try {
                listener.accept(event);
              } catch (Throwable t) {
                ThrowableUtil.report(t);
              }
            }
          });
    } catch (RejectedExecutionException _) {
      // The window closed between the check and the delivery.
    }
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

  /** The last part of {@code suggested}, without the separators of a path, which a server sends. */
  private static String fileName(String suggested) {
    String name = suggested.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).strip();
    return name.isEmpty() || name.equals(".") || name.equals("..") ? "download" : name;
  }

  static Path unique(Path directory, String suggested) {
    String name = WindowDownloads.fileName(suggested);
    Path path = directory.resolve(name);
    int dot = name.lastIndexOf('.');
    String stem = dot > 0 ? name.substring(0, dot) : name;
    String extension = dot > 0 ? name.substring(dot) : "";
    for (int number = 1; Files.exists(path); number++) {
      path = directory.resolve(stem + " (" + number + ")" + extension);
    }
    return path;
  }
}
