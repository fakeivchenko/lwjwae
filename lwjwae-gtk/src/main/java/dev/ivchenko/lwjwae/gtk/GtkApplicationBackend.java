package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.AbstractApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.gtk.binding.Glib;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.gtk.binding.WebKit;
import dev.ivchenko.lwjwae.util.MimeTypeUtil;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A window backed by GTK 3 and WebKitGTK 4.1, bound entirely through the Foreign Function and
 * Memory API, without JNI and without native artifacts of its own.
 *
 * <p>Instances are safe to use from any thread. Every call is forwarded to the shared GTK thread.
 * Native callbacks are static and dispatch through {@link CallbackRegistry}, so a single upcall
 * stub serves every window instead of one stub per instance.
 */
public class GtkApplicationBackend extends AbstractApplicationBackend {
  private static final String ERROR_DOMAIN = "lwjwae";

  private static final CallbackRegistry<GtkApplicationBackend> WINDOWS = new CallbackRegistry<>();
  private static final CallbackRegistry<CompletableFuture<String>> PENDING_EVALUATIONS =
      new CallbackRegistry<>();

  private static final MemorySegment ON_DESTROY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkApplicationBackend.class,
          "onDestroy",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.WIDGET_CALLBACK);
  private static final MemorySegment ON_LOAD_CHANGED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkApplicationBackend.class,
          "onLoadChanged",
          MethodType.methodType(void.class, MemorySegment.class, int.class, MemorySegment.class),
          Signatures.LOAD_CHANGED_CALLBACK);
  private static final MemorySegment ON_LOAD_FAILED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkApplicationBackend.class,
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
          GtkApplicationBackend.class,
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
          GtkApplicationBackend.class,
          "onEvaluationReady",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.G_ASYNC_READY_CALLBACK);
  private static final MemorySegment ON_BRIDGE_MESSAGE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkApplicationBackend.class,
          "onBridgeMessage",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.SCRIPT_MESSAGE_CALLBACK);
  private static final MemorySegment ON_RESOURCE_REQUEST =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkApplicationBackend.class,
          "onResourceRequest",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.URI_SCHEME_REQUEST_CALLBACK);

  private final long id;

  private volatile MemorySegment window;
  private volatile MemorySegment webView;
  private volatile MemorySegment userContentManager;

  /** Creates a window with {@link ApplicationParameters#createDefault()}. */
  public GtkApplicationBackend() {
    this(ApplicationParameters.createDefault());
  }

  /**
   * Creates the native window on the GTK thread and returns when it exists. The window is hidden
   * until {@link #show()}.
   *
   * @throws IllegalStateException If GTK can't open a display.
   */
  public GtkApplicationBackend(ApplicationParameters parameters) {
    super(GtkDispatcher.instance(), parameters);
    this.id = WINDOWS.register(this);
    try {
      this.dispatcher().run(() -> this.createWindow(parameters));
    } catch (RuntimeException | Error e) {
      WINDOWS.unregister(this.id);
      throw e;
    }
  }

  /** Builds the native widgets. Runs on the GTK thread, once, from the constructor. */
  private void createWindow(ApplicationParameters parameters) {
    WebKit.retainDefaultWebContext();
    WebKit.registerUriScheme(ResourceUtil.SCHEME, ON_RESOURCE_REQUEST);

    MemorySegment newWindow = Gtk.windowNew(Gtk.WINDOW_TOPLEVEL);
    Gtk.windowSetTitle(newWindow, parameters.title());
    Gtk.windowSetDefaultSize(newWindow, parameters.width(), parameters.height());

    MemorySegment userData = CallbackRegistry.userData(this.id);

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
  public String engine() {
    return "WebKitGTK " + WebKit.version();
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
   * resource}: the backend is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the window owns the backend and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onDestroy(MemorySegment widget, MemorySegment userData) {
    try {
      GtkApplicationBackend backend = WINDOWS.unregister(userData);
      if (backend != null) {
        backend.handleDestroyed();
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the backend is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the window owns the backend and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onLoadChanged(MemorySegment webView, int loadEvent, MemorySegment userData) {
    try {
      GtkApplicationBackend backend = WINDOWS.lookup(userData);
      if (backend == null) {
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
        backend.emitLoad(LoadEvent.of(state, WebKit.uri(webView)));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the backend is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the window owns the backend and closes it, this method only
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
      GtkApplicationBackend backend = WINDOWS.lookup(userData);
      if (backend != null) {
        backend.emitLoad(
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
   * resource}: the backend is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the window owns the backend and closes it, this method only
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
      GtkApplicationBackend backend = WINDOWS.lookup(userData);
      if (backend != null && WebKit.isDeveloperExtrasEnabled(webView)) {
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
   * resource}: the backend is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the window owns the backend and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onBridgeMessage(
      MemorySegment manager, MemorySegment javascriptResult, MemorySegment userData) {
    try {
      GtkApplicationBackend backend = WINDOWS.lookup(userData);
      if (backend == null) {
        return;
      }

      backend.handleBridgeMessage(WebKit.scriptMessageText(javascriptResult));
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Serves one {@code app://} request from the classpath.
   *
   * <p>This handler isn't tied to a window. The scheme is registered on the process-wide default
   * web context, so a single handler answers for every web view.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onResourceRequest(MemorySegment request, MemorySegment userData) {
    String path = "";
    try {
      path = WebKit.uriSchemeRequestPath(request);
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
