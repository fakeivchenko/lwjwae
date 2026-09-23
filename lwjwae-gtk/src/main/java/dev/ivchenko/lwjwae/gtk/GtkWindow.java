package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.gtk.binding.Gdk;
import dev.ivchenko.lwjwae.gtk.binding.Glib;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.gtk.binding.WebKit;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A window backed by GTK 3 and WebKitGTK 4.1, opened by a {@link GtkApplication}.
 *
 * <p>Instances are safe to use from any thread. Every call is forwarded to the shared GTK thread.
 * Native callbacks are static and dispatch through {@link CallbackRegistry}, so a single upcall
 * stub serves every window instead of one stub per instance.
 */
public class GtkWindow extends AbstractWindow {
  private static final CallbackRegistry<GtkWindow> WINDOWS = new CallbackRegistry<>();
  private static final CallbackRegistry<CompletableFuture<String>> PENDING_EVALUATIONS =
      new CallbackRegistry<>();

  private static final MemorySegment ON_DESTROY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
          "onDestroy",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.WIDGET_CALLBACK);
  private static final MemorySegment ON_LOAD_CHANGED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
          "onLoadChanged",
          MethodType.methodType(void.class, MemorySegment.class, int.class, MemorySegment.class),
          Signatures.LOAD_CHANGED_CALLBACK);
  private static final MemorySegment ON_LOAD_FAILED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
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
          GtkWindow.class,
          "onContextMenu",
          MethodType.methodType(
              int.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class),
          Signatures.CONTEXT_MENU_CALLBACK);
  private static final MemorySegment ON_EVALUATION_READY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
          "onEvaluationReady",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.G_ASYNC_READY_CALLBACK);
  private static final MemorySegment ON_BRIDGE_MESSAGE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
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
  GtkWindow(GtkApplication application, long id, WindowParameters parameters) {
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
    MemorySegment newWindow = Gtk.windowNew(Gtk.WINDOW_TOPLEVEL);
    Gtk.windowSetTitle(newWindow, parameters.title());
    Gtk.windowSetDefaultSize(newWindow, parameters.width(), parameters.height());
    if (parameters.centered()) {
      Gtk.windowSetPosition(newWindow, Gtk.WIN_POS_CENTER);
    } else if (parameters.hasPosition()) {
      Gtk.windowMove(newWindow, parameters.x(), parameters.y());
    }

    MemorySegment userData = CallbackRegistry.userData(this.callbackId);

    // Connect before registering, otherwise early messages race the signal handler.
    MemorySegment manager = WebKit.userContentManagerNew();
    Glib.signalConnect(
        manager, "script-message-received::" + BridgeProtocol.CHANNEL, ON_BRIDGE_MESSAGE, userData);
    if (!WebKit.registerScriptMessageHandler(manager, BridgeProtocol.CHANNEL)) {
      throw new IllegalStateException(
          "Message handler '" + BridgeProtocol.CHANNEL + "' already registered");
    }

    MemorySegment newWebView = WebKit.webViewNew(manager);
    Gtk.containerAdd(newWindow, newWebView);

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
    return this.dispatcher().call(() -> Gtk.windowGetSize(this.window())[0]);
  }

  @Override
  public int height() {
    return this.dispatcher().call(() -> Gtk.windowGetSize(this.window())[1]);
  }

  @Override
  public void size(int width, int height) {
    this.dispatcher()
        .run(
            () -> {
              Gtk.windowSetDefaultSize(this.window(), width, height);
              Gtk.windowResize(this.window(), width, height);
            });
  }

  @Override
  public WindowPosition position() {
    return this.dispatcher()
        .call(
            () -> {
              int[] position = Gtk.windowGetPosition(this.window());
              return new WindowPosition(position[0], position[1]);
            });
  }

  @Override
  public void position(int x, int y) {
    this.dispatcher().run(() -> Gtk.windowMove(this.window(), x, y));
  }

  /**
   * Before the window is mapped, {@code GTK_WIN_POS_CENTER} lets GTK place it. After that, the
   * position of the frame is the work area of its monitor minus its own size, halved. On Wayland,
   * neither the move nor the read of the position does anything, so only the first form has any
   * effect there, and only if the compositor honors it.
   */
  @Override
  public void center() {
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window();
              MemorySegment gdkWindow = Gtk.widgetGetWindow(current);
              if (gdkWindow.equals(MemorySegment.NULL) || Gdk.isWayland()) {
                Gtk.windowSetPosition(current, Gtk.WIN_POS_CENTER);
                return;
              }
              int[] area = Gdk.workareaAt(gdkWindow);
              int[] size = Gtk.windowGetSize(current);
              Gtk.windowMove(
                  current, area[0] + (area[2] - size[0]) / 2, area[1] + (area[3] - size[1]) / 2);
            });
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
    this.dispatcher().run(() -> Gtk.widgetShowAll(this.window()));
  }

  @Override
  public void close() {
    if (this.isClosed()) {
      return;
    }

    // gtk_widget_destroy() emits "destroy" synchronously, which is what completes the close.
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window;
              if (!this.isClosed() && current != null) {
                Gtk.widgetDestroy(current);
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
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onDestroy(MemorySegment widget, MemorySegment userData) {
    try {
      GtkWindow window = WINDOWS.unregister(userData);
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
      GtkWindow window = WINDOWS.lookup(userData);
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
      GtkWindow window = WINDOWS.lookup(userData);
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
      MemorySegment webView,
      MemorySegment menu,
      MemorySegment event,
      MemorySegment hitTest,
      MemorySegment userData) {
    try {
      GtkWindow window = WINDOWS.lookup(userData);
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
      MemorySegment manager, MemorySegment javascriptResult, MemorySegment userData) {
    try {
      GtkWindow window = WINDOWS.lookup(userData);
      if (window == null) {
        return;
      }

      window.handleBridgeMessage(WebKit.scriptMessageText(javascriptResult));
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
