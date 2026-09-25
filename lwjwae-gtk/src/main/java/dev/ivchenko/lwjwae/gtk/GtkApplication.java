package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.FreedesktopNotifier;
import dev.ivchenko.lwjwae.glib.StatusNotifierTray;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk.binding.AppIndicator;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.gtk.binding.WebKit;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
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

  private FreedesktopNotifier notifier;

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
    return this.notifier().show(notification, closed);
  }

  /** The notifier, connected to the bus on the first notification rather than at startup. */
  private synchronized FreedesktopNotifier notifier() {
    if (this.notifier == null) {
      this.notifier = new FreedesktopNotifier(this.dispatcher(), this.parameters().name());
    }
    return this.notifier;
  }

  @Override
  protected void onClose() {
    FreedesktopNotifier current;
    synchronized (this) {
      current = this.notifier;
      this.notifier = null;
    }
    if (current != null) {
      current.close();
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
      path = WebKit.uriSchemeRequestPath(request);
      if (path != null && path.startsWith(RpcExchange.PATH_PREFIX)) {
        GtkWindow window = GtkWindow.ofWebView(WebKit.uriSchemeRequestWebView(request));
        if (window != null) {
          window.rpc(new GtkRpcExchange(GtkDispatcher.instance(), request, path));
          return;
        }
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
