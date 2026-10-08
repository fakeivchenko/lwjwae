package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.cookie.Cookies;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.DesktopServices;
import dev.ivchenko.lwjwae.glib.DesktopTheme;
import dev.ivchenko.lwjwae.glib.PortalDialogs;
import dev.ivchenko.lwjwae.glib.SecretServiceSecrets;
import dev.ivchenko.lwjwae.glib.StatusNotifierTray;
import dev.ivchenko.lwjwae.glib.WebKitCookies;
import dev.ivchenko.lwjwae.glib.X11Shortcuts;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk4.binding.Gtk;
import dev.ivchenko.lwjwae.gtk4.binding.Signatures;
import dev.ivchenko.lwjwae.gtk4.binding.WebKit;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import dev.ivchenko.lwjwae.secret.Secrets;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.taskbar.TaskbarProgress;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.util.MimeTypeUtil;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Consumer;

/**
 * An application backed by GTK 4 and WebKitGTK 6.0, bound entirely through the Foreign Function and
 * Memory API, without JNI and without native artifacts of its own.
 *
 * <p>The application owns what GTK and WebKit keep per process: the GTK thread, through {@link
 * Gtk4Dispatcher}, and the default web context, which serves {@code app://} for every web view.
 * Both outlive the application, because GTK can't be initialized twice and a web context can't be
 * unregistered, so creating the application only makes sure that they exist. Each window is a
 * {@link Gtk4Window} on that thread, each tray icon a {@link StatusNotifierTray}, and each
 * notification a {@link dev.ivchenko.lwjwae.glib.FreedesktopNotification}.
 */
public class Gtk4Application extends AbstractApplication {
  private static final String ERROR_DOMAIN = "lwjwae";

  private static final MemorySegment ON_RESOURCE_REQUEST =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Application.class,
          "onResourceRequest",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.URI_SCHEME_REQUEST_CALLBACK);

  private static final MemorySegment ON_XLIB_EVENT =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Application.class,
          "onXlibEvent",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.X_EVENT_CALLBACK);

  /** Whether the handler that hands key presses to {@link X11Shortcuts} is in, once a process. */
  private static boolean hooked;

  private final DesktopServices desktop;
  private volatile DesktopTheme themeWatcher;

  /** Creates an application with {@link ApplicationParameters#createDefault()}. */
  public Gtk4Application() {
    this(ApplicationParameters.createDefault());
  }

  /**
   * Starts the GTK thread if it isn't running and prepares the web context. Opens no window.
   *
   * @throws IllegalStateException If GTK can't open a display.
   */
  public Gtk4Application(ApplicationParameters parameters) {
    super(Gtk4Dispatcher.instance(), parameters);
    this.desktop =
        new DesktopServices(
            Gtk4Dispatcher.instance(),
            parameters.name(),
            Gtk::isX11,
            Gtk4Application::xlibDisplay,
            Gtk::trapped);
    // Not this.dispatcher(): a call on this from the constructor lets a subclass see it half-built.
    Gtk4Dispatcher.instance()
        .run(
            () -> {
              WebKit.retainDefaultWebContext();
              WebKit.registerUriScheme(ResourceUtil.SCHEME, ON_RESOURCE_REQUEST);
              this.themeWatcher = new DesktopTheme(Gtk.settingsGetDefault(), this::themeChanged);
            });
  }

  @Override
  public String engine() {
    return "WebKitGTK " + WebKit.version();
  }

  @Override
  protected Secrets createSecrets(String service) {
    return new SecretServiceSecrets(service);
  }

  @Override
  protected Cookies createCookies() {
    return new WebKitCookies(
        this.dispatcher(), WebKit.LIBRARY, this.dispatcher().call(WebKit::cookieManager));
  }

  @Override
  protected Clipboard createClipboard() {
    return new Gtk4Clipboard(this.dispatcher());
  }

  @Override
  public List<Screen> screens() {
    return this.dispatcher().call(Gtk4Screens::all);
  }

  @Override
  protected AbstractWindow createWindow(long id, WindowParameters parameters) {
    return new Gtk4Window(this, id, parameters);
  }

  @Override
  protected Tray createTray(TrayIcon icon, Consumer<Tray> closed) {
    return new StatusNotifierTray(this.dispatcher(), icon, closed);
  }

  @Override
  protected void launchExternal(String url) {
    this.dispatcher().run(() -> Glib.launchDefaultForUri(url, PortalDialogs.originalEnvironment()));
  }

  @Override
  protected NotificationHandle createNotification(
      Notification notification, Consumer<NotificationHandle> closed) {
    return this.desktop.showNotification(notification, closed);
  }

  @Override
  protected EventSubscription bindGlobalShortcut(Shortcut shortcut, Runnable pressed) {
    return this.desktop.bindShortcut(shortcut, pressed);
  }

  @Override
  protected void showProgress(TaskbarProgress progress) {
    this.desktop.showProgress(progress);
  }

  @Override
  protected void showBadgeCount(int count) {
    this.desktop.showBadgeCount(count);
  }

  /**
   * The Xlib {@code Display}, with the hook that hands the key presses of X to {@link X11Shortcuts}
   * in, once a process. Runs on the GTK thread.
   */
  private static MemorySegment xlibDisplay() {
    if (!hooked) {
      Gtk.connectXlibEvents(ON_XLIB_EVENT);
      hooked = true;
    }
    return Gtk.xlibDisplay();
  }

  @Override
  protected void onClose() {
    DesktopTheme watcher = this.themeWatcher;
    if (watcher != null) {
      watcher.close();
    }
    this.desktop.close();
  }

  /**
   * Hands every {@code XEvent} to {@link X11Shortcuts}, and takes the press of a shortcut out of
   * the way of GDK, which has no surface for it.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static int onXlibEvent(
      MemorySegment display, MemorySegment xlibEvent, MemorySegment userData) {
    try {
      return X11Shortcuts.handleEvent(xlibEvent) ? 1 : 0;
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      return 0;
    }
  }

  /**
   * Serves one {@code app://} request from the classpath.
   *
   * <p>This handler isn't tied to a window or to an application. The scheme is registered on the
   * process-wide default web context, so a single handler answers for every web view.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onResourceRequest(MemorySegment request, MemorySegment userData) {
    String path = "";
    try {
      String requested = WebKit.uriSchemeRequestPath(request);
      if (requested != null && requested.startsWith(RpcExchange.PATH_PREFIX)) {
        Gtk4Window window = Gtk4Window.ofWebView(WebKit.uriSchemeRequestWebView(request));
        if (window != null) {
          window.rpc(new Gtk4RpcExchange(Gtk4Dispatcher.instance(), request, requested));
          return;
        }
      }
      path = ResourceUtil.servedPath(requested == null ? "" : requested);
      long size = ResourceUtil.servedSize(path);
      if (size < 0 || size >= ResourceUtil.STREAM_THRESHOLD) {
        ResourceUtil.stream(new Gtk4RpcExchange(Gtk4Dispatcher.instance(), request, path), path);
        return;
      }
      byte[] content = ResourceUtil.read(path);
      MemorySegment stream = Glib.memoryInputStream(Glib.copyToNative(content), content.length);
      try {
        WebKit.uriSchemeRequestFinish(request, stream, content.length, MimeTypeUtil.of(path));
      } finally {
        Glib.unref(stream);
      }
    } catch (ResourceNotFoundException e) {
      WebKit.uriSchemeRequestFinishError(request, Glib.error(ERROR_DOMAIN, 404, e.getMessage()));
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      WebKit.uriSchemeRequestFinishError(
          request, Glib.error(ERROR_DOMAIN, 500, "Could not serve " + path));
    }
  }
}
