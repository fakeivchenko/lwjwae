package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.DesktopServices;
import dev.ivchenko.lwjwae.glib.StatusNotifierTray;
import dev.ivchenko.lwjwae.glib.X11Shortcuts;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk.binding.AppIndicator;
import dev.ivchenko.lwjwae.gtk.binding.Gdk;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.gtk.binding.WebKit;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
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
 * An application backed by GTK 3 and WebKitGTK 4.1, bound entirely through the Foreign Function and
 * Memory API, without JNI and without native artifacts of its own.
 *
 * <p>The application owns what GTK and WebKit keep per process: the GTK thread, through {@link
 * GtkDispatcher}, and the default web context, which serves {@code app://} for every web view. Both
 * outlive the application, because GTK can't be initialized twice and a web context can't be
 * unregistered, so creating the application only makes sure that they exist. Each window is a
 * {@link GtkWindow} on that thread, and each notification a {@link
 * dev.ivchenko.lwjwae.glib.FreedesktopNotification}.
 */
public class GtkApplication extends AbstractApplication {
  private static final String ERROR_DOMAIN = "lwjwae";

  private static final MemorySegment ON_RESOURCE_REQUEST =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkApplication.class,
          "onResourceRequest",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.URI_SCHEME_REQUEST_CALLBACK);

  private static final MemorySegment ON_XLIB_EVENT =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkApplication.class,
          "onXlibEvent",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.GDK_FILTER_FUNC);

  /** Whether the filter that hands key presses to {@link X11Shortcuts} is in, once a process. */
  private static boolean filtering;

  private final DesktopServices desktop;

  /** Creates an application with {@link ApplicationParameters#createDefault()}. */
  public GtkApplication() {
    this(ApplicationParameters.createDefault());
  }

  /**
   * Starts the GTK thread if it isn't running and prepares the web context. Opens no window.
   *
   * @throws IllegalStateException If GTK can't open a display.
   */
  public GtkApplication(ApplicationParameters parameters) {
    super(GtkDispatcher.instance(), parameters);
    this.desktop =
        new DesktopServices(
            GtkDispatcher.instance(),
            parameters.name(),
            Gdk::isX11,
            GtkApplication::xlibDisplay,
            Gdk::trapped);
    // Not this.dispatcher(): a call on this from the constructor lets a subclass see it half-built.
    GtkDispatcher.instance()
        .run(
            () -> {
              WebKit.retainDefaultWebContext();
              WebKit.registerUriScheme(ResourceUtil.SCHEME, ON_RESOURCE_REQUEST);
            });
  }

  @Override
  public String engine() {
    return "WebKitGTK " + WebKit.version();
  }

  @Override
  protected Clipboard createClipboard() {
    return new GtkClipboard(this.dispatcher());
  }

  @Override
  public List<Screen> screens() {
    return this.dispatcher().call(GtkScreens::all);
  }

  @Override
  protected AbstractWindow createWindow(long id, WindowParameters parameters) {
    return new GtkWindow(this, id, parameters);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The tray takes the first of three ways that works: libappindicator, when it is installed;
   * otherwise a StatusNotifierItem that the backend serves itself over D-Bus, when the session has
   * a StatusNotifier host; otherwise {@code GtkStatusIcon}, which needs X11 and a legacy tray. The
   * first two reach the same panels, KDE and GNOME with its AppIndicator extension among them.
   */
  @Override
  protected Tray createTray(TrayIcon icon, Consumer<Tray> closed) {
    if (!AppIndicator.isAvailable()) {
      try {
        return new StatusNotifierTray(this.dispatcher(), icon, closed);
      } catch (UnsupportedOperationException _) {
        // No StatusNotifier host on the bus: the legacy tray is the one left.
      }
    }
    return new GtkTray(this.dispatcher(), icon, closed);
  }

  @Override
  protected void launchExternal(String url) {
    this.dispatcher().run(() -> Glib.launchDefaultForUri(url));
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

  /**
   * The Xlib {@code Display}, with the hook that hands the key presses of X to {@link X11Shortcuts}
   * in, once a process. Runs on the GTK thread.
   */
  private static MemorySegment xlibDisplay() {
    if (!filtering) {
      Gdk.addEventFilter(ON_XLIB_EVENT, MemorySegment.NULL);
      filtering = true;
    }
    return Gdk.xlibDisplay();
  }

  @Override
  protected void onClose() {
    this.desktop.close();
  }

  /**
   * Hands every {@code XEvent} to {@link X11Shortcuts}, and takes the press of a shortcut out of
   * the way of GDK, which has no window for it.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static int onXlibEvent(
      MemorySegment xlibEvent, MemorySegment event, MemorySegment userData) {
    try {
      return X11Shortcuts.handleEvent(xlibEvent) ? Gdk.FILTER_REMOVE : Gdk.FILTER_CONTINUE;
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      return Gdk.FILTER_CONTINUE;
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
        GtkWindow window = GtkWindow.ofWebView(WebKit.uriSchemeRequestWebView(request));
        if (window != null) {
          window.rpc(new GtkRpcExchange(GtkDispatcher.instance(), request, requested));
          return;
        }
      }
      path = ResourceUtil.servedPath(requested == null ? "" : requested);
      long size = ResourceUtil.servedSize(path);
      if (size < 0 || size >= ResourceUtil.STREAM_THRESHOLD) {
        ResourceUtil.stream(new GtkRpcExchange(GtkDispatcher.instance(), request, path), path);
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
