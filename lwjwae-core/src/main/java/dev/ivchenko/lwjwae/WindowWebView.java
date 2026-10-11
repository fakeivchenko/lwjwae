package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.FileDropEvent;
import dev.ivchenko.lwjwae.internal.Listeners;
import dev.ivchenko.lwjwae.permission.PermissionDecision;
import dev.ivchenko.lwjwae.permission.PermissionKind;
import dev.ivchenko.lwjwae.permission.PermissionRequest;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The web view of one window as an engine, and the handlers that decide for it.
 *
 * <p>The zoom and the developer tools live in the engine, so they are read and set there, on the UI
 * thread, rather than kept here: a page can't change them, and the engine is the one that knows.
 * The handlers and the listeners of a drop are kept here, since the backend asks for them from the
 * callbacks of the engine, see {@link AbstractWindow#permissionRequested} and its siblings.
 */
final class WindowWebView implements WebView {
  private final AbstractWindow window;
  private final Listeners<FileDropEvent> fileDropListeners = new Listeners<>("lwjwae-file-drop");
  private volatile Consumer<String> externalLinkHandler;
  private volatile Function<PermissionRequest, PermissionDecision> permissionHandler;

  WindowWebView(AbstractWindow window) {
    this.window = window;
  }

  @Override
  public double zoom() {
    this.window.checkOpen();
    return this.window.dispatcher().call(this.window::currentZoom);
  }

  @Override
  public void zoom(double factor) {
    this.window.checkOpen();
    if (!(factor >= MINIMUM_ZOOM && factor <= MAXIMUM_ZOOM)) {
      throw new IllegalArgumentException(
          "The zoom is from " + MINIMUM_ZOOM + " to " + MAXIMUM_ZOOM + ": " + factor);
    }
    this.window.dispatcher().run(() -> this.window.presentZoom(factor));
  }

  @Override
  public boolean isDevToolsEnabled() {
    this.window.checkOpen();
    return this.window.dispatcher().call(this.window::currentDevTools);
  }

  @Override
  public void devToolsEnabled(boolean devToolsEnabled) {
    this.window.checkOpen();
    this.window.dispatcher().run(() -> this.window.presentDevTools(devToolsEnabled));
  }

  @Override
  public void externalLinkHandler(Consumer<String> handler) {
    this.externalLinkHandler = handler;
  }

  @Override
  public void permissionHandler(Function<PermissionRequest, PermissionDecision> handler) {
    this.permissionHandler = handler;
  }

  @Override
  public EventSubscription onFileDrop(Consumer<FileDropEvent> listener) {
    return this.fileDropListeners.listen(Objects.requireNonNull(listener, "listener"));
  }

  /** Hands a link that leaves the application to the handler of the window, or to the system. */
  void leave(String url) {
    Consumer<String> handler = this.externalLinkHandler;
    if (handler != null) {
      handler.accept(url);
    } else {
      this.window.application().openExternal(url);
    }
  }

  /**
   * Whether the handler grants every one of {@code kinds} to the page of {@code origin}; with no
   * handler, or one that throws, {@code false}.
   */
  boolean permits(String origin, PermissionKind... kinds) {
    Function<PermissionRequest, PermissionDecision> handler = this.permissionHandler;
    if (handler == null || kinds.length == 0) {
      return false;
    }
    try {
      for (PermissionKind kind : kinds) {
        PermissionRequest request =
            new PermissionRequest(kind, WindowWebView.originOrEmpty(origin));
        if (handler.apply(request) != PermissionDecision.GRANT) {
          return false;
        }
      }
      return true;
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      return false;
    }
  }

  /** Hands a drop to the Java listeners. */
  void dropped(FileDropEvent event) {
    this.fileDropListeners.deliver(event);
  }

  /** Stops the delivery: the window is gone. */
  void shutdown() {
    this.fileDropListeners.shutdown();
  }

  /**
   * The origin of {@code url}, or an empty string for {@code null} and for a URL without one. The
   * engines tell the page by its URL, or by an origin with a trailing slash.
   */
  private static String originOrEmpty(String url) {
    if (url == null) {
      return "";
    }
    try {
      URI uri = URI.create(url);
      return uri.getScheme() == null || uri.getRawAuthority() == null
          ? ""
          : uri.getScheme() + "://" + uri.getRawAuthority();
    } catch (IllegalArgumentException _) {
      return "";
    }
  }
}
