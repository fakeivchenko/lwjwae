package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.cookie.Cookies;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.secret.Secrets;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.taskbar.TaskbarProgress;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.ComCallback;
import dev.ivchenko.lwjwae.windows.binding.Shell32;
import dev.ivchenko.lwjwae.windows.binding.TaskbarList;
import dev.ivchenko.lwjwae.windows.binding.User32;
import dev.ivchenko.lwjwae.windows.binding.WebView2;
import dev.ivchenko.lwjwae.windows.exception.ComCallFailedException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * An application backed by Win32 and WebView2, bound entirely through the Foreign Function and
 * Memory API, without JNI, without {@code WebView2Loader.dll}, and without native artifacts of its
 * own.
 *
 * <p>The application owns the {@code ICoreWebView2Environment}: one browser process, one user data
 * folder, one cookie jar, shared by every {@link WindowsWindow}. WebView2 creates it
 * asynchronously, so the constructor waits through {@link WindowsDispatcher#await} until the
 * environment exists, and a window only has to create its controller. The environment is released
 * when the application closes, after the last window.
 */
public class WindowsApplication extends AbstractApplication {
  private static final Duration CREATION_TIMEOUT = Duration.ofMinutes(1);
  private static final long THEME_POLL_MILLIS = 1000;

  private final AtomicReference<MemorySegment> environment = new AtomicReference<>();

  private WindowsNotifier notifier;

  /** The {@code ITaskbarList3} of the application, once it shows something. UI thread only. */
  private MemorySegment taskbarList;

  /** The overlay icon of the badge, or {@code null} for none. UI thread only. */
  private MemorySegment badgeIcon;

  /** Creates an application with {@link ApplicationParameters#createDefault()}. */
  public WindowsApplication() {
    this(ApplicationParameters.createDefault());
  }

  /**
   * Starts the UI thread if it isn't running and creates the WebView2 environment. Opens no window.
   *
   * @throws IllegalStateException If WebView2 doesn't answer within a minute.
   * @throws ComCallFailedException If WebView2 refuses to create the environment.
   */
  public WindowsApplication(ApplicationParameters parameters) {
    super(WindowsDispatcher.instance(), parameters);
    // Not this.dispatcher(): a call on this from the constructor lets a subclass see it half-built.
    WindowsDispatcher dispatcher = WindowsDispatcher.instance();
    CompletableFuture<MemorySegment> ready = new CompletableFuture<>();
    dispatcher.run(
        () -> {
          MemorySegment handler =
              ComCallback.completion(
                  WebView2.IID_ENVIRONMENT_COMPLETED,
                  (hresult, created) ->
                      WindowsApplication.completeEnvironment(ready, hresult, created));
          WebView2.createEnvironment(WindowsApplication.userDataFolder().toString(), handler);
          Com.release(handler);
        });
    this.themeChanged(WindowsTheme.read());
    Thread.ofVirtual().name("lwjwae-theme-watch").start(this::watchTheme);
    try {
      this.environment.set(dispatcher.await(ready, CREATION_TIMEOUT));
    } catch (Exception e) {
      throw e.getCause() instanceof RuntimeException runtime
          ? runtime
          : new IllegalStateException("WebView2 environment creation failed", e);
    }
  }

  /** Reads the theme once a second until the application closes, and reports a change. */
  private void watchTheme() {
    while (!this.isClosed()) {
      try {
        Thread.sleep(THEME_POLL_MILLIS);
        this.themeChanged(WindowsTheme.read());
      } catch (InterruptedException _) {
        return;
      } catch (RuntimeException e) {
        ThrowableUtil.report(e);
      }
    }
  }

  private static void completeEnvironment(
      CompletableFuture<MemorySegment> ready, int hresult, MemorySegment created) {
    if (hresult < 0) {
      ready.completeExceptionally(
          new ComCallFailedException("WebView2 environment creation", hresult));
      return;
    }
    Com.addRef(created);
    ready.complete(created);
  }

  /** The shared {@code ICoreWebView2Environment}, owned by this application. */
  MemorySegment environment() {
    MemorySegment current = this.environment.get();
    if (current == null) {
      throw new IllegalStateException("The application is closed");
    }
    return current;
  }

  @Override
  public String engine() {
    return this.dispatcher().call(() -> "WebView2 " + WebView2.browserVersion(this.environment()));
  }

  @Override
  protected Secrets createSecrets(String service) {
    return new WindowsSecrets(service);
  }

  @Override
  protected Cookies createCookies() {
    return new WindowsCookies(WindowsDispatcher.instance(), this::anyWebView);
  }

  @Override
  protected Clipboard createClipboard() {
    return new WindowsClipboard(WindowsDispatcher.instance());
  }

  @Override
  public List<Screen> screens() {
    return this.dispatcher().call(WindowsScreens::all);
  }

  @Override
  protected AbstractWindow createWindow(long id, WindowParameters parameters) {
    return new WindowsWindow(this, id, parameters);
  }

  @Override
  protected EventSubscription bindGlobalShortcut(Shortcut shortcut, Runnable pressed) {
    return WindowsShortcuts.bind(WindowsDispatcher.instance(), shortcut, pressed);
  }

  @Override
  protected Tray createTray(TrayIcon icon, Consumer<Tray> closed) {
    return new WindowsTray(this.dispatcher(), icon, closed);
  }

  @Override
  protected void launchExternal(String url) {
    this.dispatcher().run(() -> Shell32.open(url));
  }

  @Override
  protected NotificationHandle createNotification(
      Notification notification, Consumer<NotificationHandle> closed) {
    return this.notifier().show(notification, closed);
  }

  /** The notifier, registered and created on the first notification rather than at startup. */
  private synchronized WindowsNotifier notifier() {
    if (this.notifier == null) {
      this.notifier = new WindowsNotifier(this.dispatcher(), this.parameters().name());
    }
    return this.notifier;
  }

  @Override
  protected void showProgress(TaskbarProgress progress) {
    this.windowHandles().forEach(this::decorateTaskbarButton);
  }

  @Override
  protected void showBadgeCount(int count) {
    MemorySegment previous = this.badgeIcon;
    this.badgeIcon = count == 0 ? null : User32.iconFromPng(BadgeImage.png(count));
    this.windowHandles().forEach(this::decorateTaskbarButton);
    if (previous != null) {
      User32.destroyIcon(previous);
    }
  }

  /**
   * Shows the progress and the badge of the application on the button of {@code hwnd}, as the
   * taskbar tells a window once its button exists, and as they change. Runs on the UI thread.
   */
  void decorateTaskbarButton(MemorySegment hwnd) {
    TaskbarProgress progress = this.progress();
    int count = this.badgeCount();
    if (this.taskbarList == null) {
      if (!progress.isShown() && count == 0) {
        return;
      }
      this.taskbarList = TaskbarList.create();
    }
    TaskbarList.setProgress(
        this.taskbarList, hwnd, WindowsApplication.progressState(progress), progress.value());
    TaskbarList.setOverlayIcon(
        this.taskbarList,
        hwnd,
        this.badgeIcon == null ? MemorySegment.NULL : this.badgeIcon,
        count == 0 ? null : Integer.toString(count));
  }

  /** The view of an open window, which reaches the cookies of the profile. */
  private MemorySegment anyWebView() {
    return this.windows().stream()
        .map(WindowsWindow.class::cast)
        .map(WindowsWindow::webView)
        .filter(Objects::nonNull)
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("The cookies of WebView2 need an open window"));
  }

  private List<MemorySegment> windowHandles() {
    return this.windows().stream()
        .map(WindowsWindow.class::cast)
        .map(WindowsWindow::handle)
        .filter(Objects::nonNull)
        .toList();
  }

  private static int progressState(TaskbarProgress progress) {
    return switch (progress.state()) {
      case NONE -> TaskbarList.PROGRESS_NONE;
      case NORMAL -> TaskbarList.PROGRESS_NORMAL;
      case INDETERMINATE -> TaskbarList.PROGRESS_INDETERMINATE;
      case PAUSED -> TaskbarList.PROGRESS_PAUSED;
      case ERROR -> TaskbarList.PROGRESS_ERROR;
    };
  }

  @Override
  protected void onClose() {
    WindowsNotifier currentNotifier;
    synchronized (this) {
      currentNotifier = this.notifier;
      this.notifier = null;
    }
    if (currentNotifier != null) {
      currentNotifier.close();
    }
    MemorySegment closing = this.environment.getAndSet(null);
    if (closing != null) {
      this.dispatcher().run(() -> Com.release(closing));
    }
    this.dispatcher()
        .run(
            () -> {
              if (this.taskbarList != null) {
                Com.release(this.taskbarList);
                this.taskbarList = null;
              }
              if (this.badgeIcon != null) {
                User32.destroyIcon(this.badgeIcon);
                this.badgeIcon = null;
              }
            });
  }

  private static Path userDataFolder() {
    String local = System.getenv("LOCALAPPDATA");
    Path folder =
        Path.of(local != null ? local : System.getProperty("java.io.tmpdir"), "lwjwae", "WebView2");
    try {
      Files.createDirectories(folder);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create the WebView2 user data folder " + folder, e);
    }
    return folder;
  }
}
