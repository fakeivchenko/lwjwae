package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.FreedesktopNotifier;
import dev.ivchenko.lwjwae.glib.StatusNotifierTray;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk4.binding.Signatures;
import dev.ivchenko.lwjwae.gtk4.binding.WebKit;
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

  private FreedesktopNotifier notifier;

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
    // Not this.dispatcher(): a call on this from the constructor lets a subclass see it half-built.
    Gtk4Dispatcher.instance()
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
  protected AbstractWindow createWindow(long id, WindowParameters parameters) {
    return new Gtk4Window(this, id, parameters);
  }

  @Override
  protected Tray createTray(TrayIcon icon, Consumer<Tray> closed) {
    return new StatusNotifierTray(this.dispatcher(), icon, closed);
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
        Gtk4Window window = Gtk4Window.ofWebView(WebKit.uriSchemeRequestWebView(request));
        if (window != null) {
          window.rpc(new Gtk4RpcExchange(Gtk4Dispatcher.instance(), request, path));
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
