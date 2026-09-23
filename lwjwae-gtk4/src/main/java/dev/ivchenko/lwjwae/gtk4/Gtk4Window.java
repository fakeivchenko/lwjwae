package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk4.binding.Gtk;
import dev.ivchenko.lwjwae.gtk4.binding.Signatures;
import dev.ivchenko.lwjwae.gtk4.binding.WebKit;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A window backed by GTK 4 and WebKitGTK 6.0, opened by a {@link Gtk4Application}.
 *
 * <p>Instances are safe to use from any thread. Every call is forwarded to the shared GTK thread.
 * Native callbacks are static and dispatch through {@link CallbackRegistry}, so a single upcall
 * stub serves every window instead of one stub per instance.
 *
 * <p>GTK 4 can't place a window: it has no call to move one and no way to read where it is, on X11
 * as on Wayland. The position that {@link WindowParameters} asks for is ignored, {@link
 * #position()} answers {@code 0, 0}, and {@link #position(int, int)} and {@link #center()} do
 * nothing, which is what the API promises for Wayland; the desktop places the window.
 */
public class Gtk4Window extends AbstractWindow {
  private static final CallbackRegistry<Gtk4Window> WINDOWS = new CallbackRegistry<>();
  private static final CallbackRegistry<CompletableFuture<String>> PENDING_EVALUATIONS =
      new CallbackRegistry<>();

  private static final MemorySegment ON_DESTROY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onDestroy",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.WIDGET_CALLBACK);
  private static final MemorySegment ON_CLOSE_REQUEST =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onCloseRequest",
          MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class),
          Signatures.CLOSE_REQUEST_CALLBACK);
  private static final MemorySegment ON_LOAD_CHANGED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onLoadChanged",
          MethodType.methodType(void.class, MemorySegment.class, int.class, MemorySegment.class),
          Signatures.LOAD_CHANGED_CALLBACK);
  private static final MemorySegment ON_LOAD_FAILED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onLoadFailed",
          MethodType.methodType(
              int.class,
              MemorySegment.class,
              int.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class),
          Signatures.LOAD_FAILED_CALLBACK);
  private static final MemorySegment ON_CONTEXT_MENU =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onContextMenu",
          MethodType.methodType(
              int.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class),
          Signatures.CONTEXT_MENU_CALLBACK);
  private static final MemorySegment ON_EVALUATION_READY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onEvaluationReady",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.G_ASYNC_READY_CALLBACK);
  private static final MemorySegment ON_BRIDGE_MESSAGE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onBridgeMessage",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.SCRIPT_MESSAGE_CALLBACK);
  private final long callbackId;

  private volatile MemorySegment window;
  private volatile MemorySegment webView;
  private volatile MemorySegment userContentManager;

  /**
   * Creates the native window on the GTK thread and returns when it exists. The window is hidden
   * until {@link #show()}.
   *
   * <p>Suppressed warnings: {@code resource}: on the failure path, {@code unregister} hands back
   * this window, which looks like an unclosed resource. It is not: there is no native window left
   * to close, and the exception tells the caller that nothing was opened.
   */
  @SuppressWarnings("resource")
  Gtk4Window(Gtk4Application application, long id, WindowParameters parameters) {
    super(application, id);
    this.callbackId = WINDOWS.register(this);
    try {
      this.dispatcher().run(() -> this.createWindow(parameters));
    } catch (RuntimeException | Error e) {
      WINDOWS.unregister(this.callbackId);
      throw e;
    }
  }

  /** Builds the native widgets. Runs on the GTK thread, once, from the constructor. */
  private void createWindow(WindowParameters parameters) {
    MemorySegment newWindow = Gtk.windowNew();
    Gtk.windowSetTitle(newWindow, parameters.title());
    Gtk.windowSetDefaultSize(newWindow, parameters.width(), parameters.height());

    MemorySegment userData = CallbackRegistry.userData(this.callbackId);

    MemorySegment newWebView = WebKit.webViewNew();
    // Connect before registering, otherwise early messages race the signal handler.
    MemorySegment manager = WebKit.userContentManager(newWebView);
    Glib.signalConnect(
        manager, "script-message-received::" + BridgeProtocol.CHANNEL, ON_BRIDGE_MESSAGE, userData);
    if (!WebKit.registerScriptMessageHandler(manager, BridgeProtocol.CHANNEL)) {
      throw new IllegalStateException(
          "Message handler '" + BridgeProtocol.CHANNEL + "' already registered");
    }

    Gtk.windowSetChild(newWindow, newWebView);

    Glib.signalConnect(newWindow, "close-request", ON_CLOSE_REQUEST, userData);
    Glib.signalConnect(newWindow, "destroy", ON_DESTROY, userData);
    Glib.signalConnect(newWebView, "load-changed", ON_LOAD_CHANGED, userData);
    Glib.signalConnect(newWebView, "load-failed", ON_LOAD_FAILED, userData);
    Glib.signalConnect(newWebView, "context-menu", ON_CONTEXT_MENU, userData);

    this.window = newWindow;
    this.webView = newWebView;
    this.userContentManager = manager;
    this.installBridge();
  }

  @Override
  public String title() {
    return this.dispatcher().call(() -> Gtk.windowGetTitle(this.window()));
  }

  @Override
  public void title(String title) {
    this.dispatcher().run(() -> Gtk.windowSetTitle(this.window(), title));
  }

  @Override
  public int width() {
    return this.dispatcher().call(() -> Gtk.windowGetDefaultSize(this.window())[0]);
  }

  @Override
  public int height() {
    return this.dispatcher().call(() -> Gtk.windowGetDefaultSize(this.window())[1]);
  }

  @Override
  public void size(int width, int height) {
    this.dispatcher().run(() -> Gtk.windowSetDefaultSize(this.window(), width, height));
  }

  /** Always {@code 0, 0}: GTK 4 can't tell where a window is. */
  @Override
  public WindowPosition position() {
    this.checkOpen();
    return new WindowPosition(0, 0);
  }

  /** Does nothing: GTK 4 can't move a window. */
  @Override
  public void position(int x, int y) {
    this.checkOpen();
  }

  /** Does nothing: GTK 4 can't move a window, and the desktop places a new one. */
  @Override
  public void center() {
    this.checkOpen();
  }

  @Override
  public boolean isResizable() {
    return this.dispatcher().call(() -> Gtk.isWindowResizable(this.window()));
  }

  @Override
  public void resizable(boolean resizable) {
    this.dispatcher().run(() -> Gtk.windowSetResizable(this.window(), resizable));
  }

  @Override
  public boolean isDevToolsEnabled() {
    return this.dispatcher().call(() -> WebKit.isDeveloperExtrasEnabled(this.webView()));
  }

  @Override
  public void devToolsEnabled(boolean devToolsEnabled) {
    this.dispatcher().run(() -> WebKit.setDeveloperExtrasEnabled(this.webView(), devToolsEnabled));
  }

  @Override
  public void navigate(String url) {
    Objects.requireNonNull(url, "url");
    this.dispatcher().run(() -> WebKit.loadUri(this.webView(), url));
  }

  @Override
  public void html(String html) {
    Objects.requireNonNull(html, "html");
    this.dispatcher().run(() -> WebKit.loadHtml(this.webView(), html, null));
  }

  @Override
  public String url() {
    return this.dispatcher().call(() -> WebKit.uri(this.webView()));
  }

  @Override
  public CompletableFuture<String> eval(String script) {
    Objects.requireNonNull(script, "script");
    CompletableFuture<String> result = new CompletableFuture<>();
    long evaluationId = PENDING_EVALUATIONS.register(result);
    this.dispatcher()
        .post(
            () -> {
              try {
                WebKit.evaluateJavascript(
                    this.webView(),
                    script,
                    ON_EVALUATION_READY,
                    CallbackRegistry.userData(evaluationId));
              } catch (Throwable t) {
                CompletableFuture<String> pending = PENDING_EVALUATIONS.unregister(evaluationId);
                if (pending != null) {
                  pending.completeExceptionally(t);
                }
              }
            });
    return result;
  }

  @Override
  protected void injectOnDocumentStart(String script) {
    this.dispatcher().run(() -> WebKit.addUserScript(this.userContentManager(), script));
  }

  @Override
  protected String bridgeTransportScript() {
    return "(message) => window.webkit.messageHandlers.%s.postMessage(message)"
        .formatted(BridgeProtocol.CHANNEL);
  }

  @Override
  public void show() {
    this.dispatcher().run(() -> Gtk.windowPresent(this.window()));
  }

  @Override
  public void requestClose() {
    this.dispatcher().run(() -> Gtk.windowClose(this.window()));
  }

  @Override
  public void hide() {
    this.dispatcher().run(() -> Gtk.widgetSetVisible(this.window(), false));
  }

  @Override
  public boolean isVisible() {
    return this.dispatcher().call(() -> Gtk.isWidgetVisible(this.window()));
  }

  @Override
  public void close() {
    if (this.isClosed()) {
      return;
    }

    // gtk_window_destroy() emits "destroy" synchronously, which is what completes the close.
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window;
              if (!this.isClosed() && current != null) {
                Gtk.windowDestroy(current);
              }
            });
  }

  private MemorySegment window() {
    return this.alive(this.window);
  }

  private MemorySegment webView() {
    return this.alive(this.webView);
  }

  private MemorySegment userContentManager() {
    return this.alive(this.userContentManager);
  }

  private MemorySegment alive(MemorySegment handle) {
    this.checkOpen();
    if (handle == null) {
      throw new IllegalStateException("The window is closed");
    }
    return handle;
  }

  private void handleDestroyed() {
    this.window = null;
    this.webView = null;
    this.userContentManager = null;
    this.markClosed();
  }

  // --- signal handlers, bound by name from the upcall stubs above; signatures are GTK's ---

  /**
   * The user asked to close the window, from the title bar or the desktop. {@code TRUE} cancels the
   * close; with {@link dev.ivchenko.lwjwae.CloseAction#HIDE}, the window is hidden instead.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static int onCloseRequest(MemorySegment widget, MemorySegment userData) {
    try {
      Gtk4Window window = WINDOWS.lookup(userData);
      if (window != null && window.hidesOnCloseRequest()) {
        Gtk.widgetSetVisible(widget, false);
        return 1;
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return 0;
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onDestroy(MemorySegment widget, MemorySegment userData) {
    try {
      Gtk4Window window = WINDOWS.unregister(userData);
      if (window != null) {
        window.handleDestroyed();
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onLoadChanged(MemorySegment webView, int loadEvent, MemorySegment userData) {
    try {
      Gtk4Window window = WINDOWS.lookup(userData);
      if (window == null) {
        return;
      }

      LoadState state =
          switch (loadEvent) {
            case WebKit.LOAD_STARTED -> LoadState.STARTED;
            case WebKit.LOAD_REDIRECTED -> LoadState.REDIRECTED;
            case WebKit.LOAD_COMMITTED -> LoadState.COMMITTED;
            case WebKit.LOAD_FINISHED -> LoadState.FINISHED;
            default -> null;
          };
      if (state != null) {
        window.emitLoad(LoadEvent.of(state, WebKit.uri(webView)));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static int onLoadFailed(
      MemorySegment webView,
      int loadEvent,
      MemorySegment failingUri,
      MemorySegment error,
      MemorySegment userData) {
    try {
      Gtk4Window window = WINDOWS.lookup(userData);
      if (window != null) {
        window.emitLoad(
            LoadEvent.failed(NativeLibraries.string(failingUri), Glib.errorMessage(error)));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return 0; // FALSE: let WebKit render its own error page
  }

  /**
   * Suppresses the context menu of the engine ("Reload", "View Source") in a shipped application,
   * by returning {@code TRUE}. With the developer tools enabled, the menu stays, because "Inspect
   * Element" lives there. A page can still handle {@code contextmenu} itself either way.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static int onContextMenu(
      MemorySegment webView, MemorySegment menu, MemorySegment hitTest, MemorySegment userData) {
    try {
      Gtk4Window window = WINDOWS.lookup(userData);
      if (window != null && WebKit.isDeveloperExtrasEnabled(webView)) {
        return 0;
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return 1;
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onEvaluationReady(
      MemorySegment source, MemorySegment result, MemorySegment userData) {
    CompletableFuture<String> pending = PENDING_EVALUATIONS.unregister(userData);
    if (pending == null) {
      return;
    }

    try {
      pending.complete(WebKit.evaluateJavascriptFinish(source, result));
    } catch (Throwable t) {
      pending.completeExceptionally(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onBridgeMessage(
      MemorySegment manager, MemorySegment value, MemorySegment userData) {
    try {
      Gtk4Window window = WINDOWS.lookup(userData);
      if (window == null) {
        return;
      }

      window.handleBridgeMessage(WebKit.scriptMessageText(value));
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
