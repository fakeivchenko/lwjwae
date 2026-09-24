package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.WindowSize;
import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.MethodStub;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.macos.binding.Signatures;
import dev.ivchenko.lwjwae.macos.binding.WebKit;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import dev.ivchenko.lwjwae.util.MimeTypeUtil;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import dev.ivchenko.lwjwae.util.ScriptUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@code NSWindow} that holds a {@code WKWebView}, driven through the Objective-C runtime and
 * opened by a {@link MacApplication}.
 *
 * <p>Everything that WebKit and AppKit tell the backend arrives through one Objective-C class
 * defined at runtime, {@code LwjwaeDelegate}, whose methods are Java upcall stubs. It's the window
 * delegate, the navigation delegate, the script message handler, and the URL scheme handler of one
 * window. Each window gets its own instance, and the instance address is the key back to the
 * window.
 *
 * <p>A URL scheme handler serves the files of the application under {@code app://local/}, the same
 * scheme that WebKitGTK uses, so the same page runs unchanged on both.
 */
public class MacWindow extends AbstractWindow {
  /**
   * The page-side switch that the context menu script reads. The engine has no setting to suppress
   * its menu, so a user script cancels {@code contextmenu} unless this global is set, which {@link
   * #devToolsEnabled} does, because "Inspect Element" lives in that menu.
   */
  private static final String CONTEXT_MENU_FLAG = "__lwjwaeContextMenu";

  private static final Map<Long, MacWindow> DELEGATES = new ConcurrentHashMap<>();
  private static final CallbackRegistry<PendingEvaluation> PENDING_EVALUATIONS =
      new CallbackRegistry<>();

  private static final MemorySegment ON_WINDOW_SHOULD_CLOSE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacWindow.class,
          "onWindowShouldClose",
          MethodType.methodType(
              boolean.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.DELEGATE_1_BOOL);
  private static final MemorySegment ON_WINDOW_WILL_CLOSE = delegateStub("onWindowWillClose", 1);
  private static final MemorySegment ON_WINDOW_CHANGED = delegateStub("onWindowChanged", 1);
  private static final MemorySegment ON_DID_START =
      delegateStub("onDidStartProvisionalNavigation", 2);
  private static final MemorySegment ON_DID_COMMIT = delegateStub("onDidCommitNavigation", 2);
  private static final MemorySegment ON_DID_FINISH = delegateStub("onDidFinishNavigation", 2);
  private static final MemorySegment ON_DID_FAIL = delegateStub("onDidFailNavigation", 3);
  private static final MemorySegment ON_DID_RECEIVE_MESSAGE =
      delegateStub("onDidReceiveScriptMessage", 2);
  private static final MemorySegment ON_START_TASK = delegateStub("onStartUrlSchemeTask", 2);
  private static final MemorySegment ON_STOP_TASK = delegateStub("onStopUrlSchemeTask", 2);
  private static final MemorySegment ON_EVALUATION_COMPLETE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacWindow.class,
          "onEvaluationComplete",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.COMPLETION_BLOCK);

  private static final MemorySegment DELEGATE_CLASS =
      ObjC.defineClass(
          "LwjwaeDelegate",
          ObjC.cls("NSObject"),
          Map.ofEntries(
              Map.entry("windowShouldClose:", new MethodStub(ON_WINDOW_SHOULD_CLOSE, "B@:@")),
              Map.entry("windowWillClose:", new MethodStub(ON_WINDOW_WILL_CLOSE, "v@:@")),
              Map.entry(
                  "webView:didStartProvisionalNavigation:", new MethodStub(ON_DID_START, "v@:@@")),
              Map.entry("webView:didCommitNavigation:", new MethodStub(ON_DID_COMMIT, "v@:@@")),
              Map.entry("webView:didFinishNavigation:", new MethodStub(ON_DID_FINISH, "v@:@@")),
              Map.entry(
                  "webView:didFailProvisionalNavigation:withError:",
                  new MethodStub(ON_DID_FAIL, "v@:@@@")),
              Map.entry(
                  "webView:didFailNavigation:withError:", new MethodStub(ON_DID_FAIL, "v@:@@@")),
              Map.entry(
                  "userContentController:didReceiveScriptMessage:",
                  new MethodStub(ON_DID_RECEIVE_MESSAGE, "v@:@@")),
              Map.entry("webView:startURLSchemeTask:", new MethodStub(ON_START_TASK, "v@:@@")),
              Map.entry("webView:stopURLSchemeTask:", new MethodStub(ON_STOP_TASK, "v@:@@")),
              Map.entry("windowDidResize:", new MethodStub(ON_WINDOW_CHANGED, "v@:@")),
              Map.entry("windowDidMove:", new MethodStub(ON_WINDOW_CHANGED, "v@:@")),
              Map.entry("windowDidBecomeKey:", new MethodStub(ON_WINDOW_CHANGED, "v@:@")),
              Map.entry("windowDidResignKey:", new MethodStub(ON_WINDOW_CHANGED, "v@:@")),
              Map.entry("windowDidMiniaturize:", new MethodStub(ON_WINDOW_CHANGED, "v@:@")),
              Map.entry("windowDidDeminiaturize:", new MethodStub(ON_WINDOW_CHANGED, "v@:@")),
              Map.entry("windowDidEnterFullScreen:", new MethodStub(ON_WINDOW_CHANGED, "v@:@")),
              Map.entry("windowDidExitFullScreen:", new MethodStub(ON_WINDOW_CHANGED, "v@:@"))));

  private volatile MemorySegment window;
  private volatile MemorySegment webView;
  private volatile MemorySegment userContentController;
  private volatile MemorySegment delegate;
  private volatile String loading = "about:blank";
  private volatile String reportedFailure;
  private volatile WindowSize minimumSize = WindowSize.NONE;
  private volatile WindowSize maximumSize = WindowSize.NONE;

  /**
   * Creates the window and the web view on the main thread and returns when they exist. The window
   * is hidden until {@link #show()}.
   */
  MacWindow(MacApplication application, long id, WindowParameters parameters) {
    super(application, id);
    this.dispatcher().run(() -> this.createWindow(parameters));
  }

  /** Builds the delegate, the web view, and the window. Runs on the main thread, once. */
  private void createWindow(WindowParameters parameters) {
    MemorySegment newDelegate = ObjC.send(ObjC.send(DELEGATE_CLASS, "alloc"), "init");
    DELEGATES.put(newDelegate.address(), this);

    MemorySegment configuration = WebKit.configuration();
    WebKit.setUrlSchemeHandler(configuration, newDelegate, ResourceUtil.SCHEME);
    MemorySegment controller = Foundation.retain(WebKit.userContentController(configuration));
    WebKit.addScriptMessageHandler(controller, newDelegate, BridgeProtocol.CHANNEL);

    MemorySegment newWebView =
        WebKit.webView(parameters.width(), parameters.height(), configuration);
    Foundation.release(configuration);
    WebKit.setNavigationDelegate(newWebView, newDelegate);

    MemorySegment newWindow =
        AppKit.window(parameters.width(), parameters.height(), parameters.title());
    AppKit.setContentView(newWindow, newWebView);
    AppKit.setDelegate(newWindow, newDelegate);
    // AppKit.window centers; a requested position wins over that, a requested center is a no-op.
    if (parameters.hasPosition() && !parameters.centered()) {
      AppKit.setFramePosition(newWindow, parameters.x(), parameters.y());
    }

    this.delegate = newDelegate;
    this.userContentController = controller;
    this.webView = newWebView;
    this.window = newWindow;
    this.injectOnDocumentStart(
        "document.addEventListener('contextmenu', event => { if (!window."
            + CONTEXT_MENU_FLAG
            + ") event.preventDefault(); });");
    this.installBridge();
  }

  @Override
  public String title() {
    return this.dispatcher().call(() -> AppKit.title(this.window()));
  }

  @Override
  public void title(String title) {
    Objects.requireNonNull(title, "title");
    this.dispatcher().run(() -> AppKit.setTitle(this.window(), title));
  }

  @Override
  public int width() {
    return this.dispatcher().call(() -> AppKit.contentSize(this.window())[0]);
  }

  @Override
  public int height() {
    return this.dispatcher().call(() -> AppKit.contentSize(this.window())[1]);
  }

  @Override
  public void size(int width, int height) {
    this.dispatcher().run(() -> AppKit.setContentSize(this.window(), width, height));
  }

  @Override
  public WindowPosition position() {
    return this.dispatcher()
        .call(
            () -> {
              int[] frame = AppKit.framePosition(this.window());
              return new WindowPosition(frame[0], frame[1]);
            });
  }

  @Override
  public void position(int x, int y) {
    this.dispatcher().run(() -> AppKit.setFramePosition(this.window(), x, y));
  }

  @Override
  public void center() {
    this.dispatcher().run(() -> AppKit.center(this.window()));
  }

  @Override
  public boolean isResizable() {
    return this.dispatcher()
        .call(() -> (AppKit.styleMask(this.window()) & AppKit.STYLE_RESIZABLE) != 0);
  }

  @Override
  public void resizable(boolean resizable) {
    this.dispatcher()
        .run(
            () -> {
              long styleMask = AppKit.styleMask(this.window());
              AppKit.setStyleMask(
                  this.window(),
                  resizable
                      ? styleMask | AppKit.STYLE_RESIZABLE
                      : styleMask & ~AppKit.STYLE_RESIZABLE);
            });
  }

  @Override
  public WindowSize minimumSize() {
    return this.minimumSize;
  }

  @Override
  public void minimumSize(int width, int height) {
    this.minimumSize = new WindowSize(width, height);
    this.dispatcher().run(this::applySizeLimits);
  }

  @Override
  public WindowSize maximumSize() {
    return this.maximumSize;
  }

  @Override
  public void maximumSize(int width, int height) {
    this.maximumSize = new WindowSize(width, height);
    this.dispatcher().run(this::applySizeLimits);
  }

  /**
   * AppKit keeps the user within the limits but leaves a window that is outside them already as it
   * is, so the content is resized into them here.
   */
  private void applySizeLimits() {
    WindowSize minimum = this.minimumSize;
    WindowSize maximum = this.maximumSize;
    MemorySegment current = this.window();
    AppKit.setContentSizeLimits(
        current, minimum.width(), minimum.height(), maximum.width(), maximum.height());
    int[] size = AppKit.contentSize(current);
    int width = clamp(size[0], minimum.width(), maximum.width());
    int height = clamp(size[1], minimum.height(), maximum.height());
    if (width != size[0] || height != size[1]) {
      AppKit.setContentSize(current, width, height);
    }
  }

  /** {@code value} within {@code minimum} and {@code maximum}, where zero means no limit. */
  private static int clamp(int value, int minimum, int maximum) {
    int atLeast = Math.max(value, minimum);
    return maximum > 0 ? Math.min(atLeast, maximum) : atLeast;
  }

  @Override
  public boolean isMinimized() {
    return this.dispatcher().call(() -> AppKit.isMiniaturized(this.window()));
  }

  @Override
  public void minimize() {
    this.dispatcher().run(() -> AppKit.setMiniaturized(this.window(), true));
  }

  @Override
  public boolean isMaximized() {
    return this.dispatcher().call(() -> AppKit.isZoomed(this.window()));
  }

  @Override
  public void maximize() {
    this.dispatcher().run(() -> AppKit.setZoomed(this.window(), true));
  }

  @Override
  public void restore() {
    this.dispatcher()
        .run(
            () -> {
              AppKit.setMiniaturized(this.window(), false);
              AppKit.setZoomed(this.window(), false);
            });
  }

  @Override
  public boolean isFullscreen() {
    return this.dispatcher()
        .call(() -> (AppKit.styleMask(this.window()) & AppKit.STYLE_FULL_SCREEN) != 0);
  }

  @Override
  public void fullscreen(boolean fullscreen) {
    this.dispatcher().run(() -> AppKit.setFullScreen(this.window(), fullscreen));
  }

  @Override
  public boolean isAlwaysOnTop() {
    return this.dispatcher().call(() -> AppKit.isFloating(this.window()));
  }

  @Override
  public void alwaysOnTop(boolean alwaysOnTop) {
    this.dispatcher().run(() -> AppKit.setFloating(this.window(), alwaysOnTop));
  }

  @Override
  public boolean isFocused() {
    return this.dispatcher().call(() -> AppKit.isKeyWindow(this.window()));
  }

  @Override
  public void focus() {
    this.dispatcher()
        .run(
            () -> {
              AppKit.setMiniaturized(this.window(), false);
              AppKit.show(this.window());
              AppKit.activate();
            });
  }

  @Override
  public boolean isDevToolsEnabled() {
    return this.dispatcher().call(() -> WebKit.isDeveloperExtrasEnabled(this.webView()));
  }

  @Override
  public void devToolsEnabled(boolean devToolsEnabled) {
    this.dispatcher()
        .run(
            () -> {
              WebKit.setDeveloperExtrasEnabled(this.webView(), devToolsEnabled);
              // Once for documents loaded from now on, once for the document already on screen.
              String flag = "window." + CONTEXT_MENU_FLAG + " = " + devToolsEnabled + ";";
              this.injectOnDocumentStart(flag);
              this.eval(flag);
            });
  }

  @Override
  public void navigate(String url) {
    Objects.requireNonNull(url, "url");
    this.dispatcher()
        .run(
            () -> {
              this.loading = url;
              this.reportedFailure = null;
              WebKit.loadUrl(this.webView(), url);
            });
  }

  @Override
  public String url() {
    return this.dispatcher()
        .call(
            () -> {
              String url = WebKit.url(this.webView());
              return url == null ? "about:blank" : url;
            });
  }

  @Override
  public void html(String html) {
    Objects.requireNonNull(html, "html");
    this.dispatcher()
        .run(
            () -> {
              this.reportedFailure = null;
              WebKit.loadHtml(this.webView(), html, this.resourceUrl(""));
            });
  }

  /**
   * {@inheritDoc}
   *
   * <p>The script is wrapped like on the other engines. The text of the caller runs in the global
   * scope, and the outcome comes back as one tagged string, so every value arrives in its {@code
   * String()} form, and a thrown error arrives as a {@link ScriptEvaluationFailedException} that
   * carries its message instead of the generic message of WebKit.
   */
  @Override
  public CompletableFuture<String> eval(String script) {
    Objects.requireNonNull(script, "script");
    CompletableFuture<String> result = new CompletableFuture<>();
    String wrapped = ScriptUtil.taggedEvaluation(script);
    this.dispatcher()
        .post(
            () -> {
              try {
                Arena arena = Arena.ofAuto();
                long id = PENDING_EVALUATIONS.register(new PendingEvaluation(result, arena));
                WebKit.evaluateJavaScript(
                    this.webView(), wrapped, ObjC.block(arena, ON_EVALUATION_COMPLETE, id));
              } catch (Throwable t) {
                result.completeExceptionally(t);
              }
            });
    return result;
  }

  @Override
  public void show() {
    this.dispatcher()
        .run(
            () -> {
              AppKit.show(this.window());
              AppKit.activate();
            });
  }

  @Override
  public void requestClose() {
    this.dispatcher().run(() -> AppKit.performClose(this.window()));
  }

  @Override
  public void hide() {
    this.dispatcher().run(() -> AppKit.hide(this.window()));
  }

  @Override
  public boolean isVisible() {
    return this.dispatcher().call(() -> AppKit.isVisible(this.window()));
  }

  @Override
  public void close() {
    if (this.isClosed()) {
      return;
    }
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window;
              if (!this.isClosed() && current != null) {
                AppKit.close(current);
              }
            });
  }

  @Override
  protected void injectOnDocumentStart(String script) {
    this.dispatcher()
        .run(() -> WebKit.addUserScript(this.alive(this.userContentController), script));
  }

  @Override
  protected String bridgeTransportScript() {
    return "(message) => window.webkit.messageHandlers.%s.postMessage(message)"
        .formatted(BridgeProtocol.CHANNEL);
  }

  private MemorySegment window() {
    return this.alive(this.window);
  }

  private MemorySegment webView() {
    return this.alive(this.webView);
  }

  private MemorySegment alive(MemorySegment handle) {
    this.checkOpen();
    if (handle == null) {
      throw new IllegalStateException("The window is closed");
    }
    return handle;
  }

  /**
   * Suppressed warnings: {@code resource}: the window is {@code AutoCloseable}, and a lookup that
   * returns it looks like an unclosed resource. It is not: the application owns the window and
   * closes it, this method only borrows it.
   */
  @SuppressWarnings("resource")
  private void handleDestroyed() {
    MemorySegment closingDelegate = this.delegate;
    if (closingDelegate != null) {
      DELEGATES.remove(closingDelegate.address());
    }

    MemorySegment closingWebView = this.webView;
    if (closingWebView != null) {
      WebKit.setNavigationDelegate(closingWebView, MemorySegment.NULL);
    }
    if (this.window != null) {
      AppKit.setDelegate(this.window, MemorySegment.NULL);
    }
    Foundation.release(this.userContentController);
    Foundation.release(closingWebView);
    Foundation.release(this.window);
    Foundation.release(closingDelegate);
    this.window = null;
    this.webView = null;
    this.userContentController = null;
    this.delegate = null;
    // Last: the application stops the run loop once its window list is empty.
    this.markClosed();
  }

  private void handleLoadFailed(MemorySegment error) {
    String url = Foundation.errorFailingUrl(error);
    if (url == null) {
      url = this.loading;
    }
    if (url.equals(this.reportedFailure)) {
      return;
    }
    this.reportedFailure = url;
    this.emitLoad(LoadEvent.failed(url, Foundation.errorDescription(error)));
  }

  /** Serves an RPC request of the page of this window. */
  void rpc(RpcExchange exchange) {
    this.serveRpc(exchange);
  }

  /** Runs {@code task} on the main thread, later, without waiting. */
  void postToMain(Runnable task) {
    this.dispatcher().post(task);
  }

  /**
   * Serves one {@code app://} request from the classpath, or fails it with the name of the missing
   * path.
   */
  private void serveResource(MemorySegment task) {
    String url = WebKit.taskUrl(task);
    String path = url.substring(url.indexOf("//") + 2);
    path = path.substring(path.indexOf('/') + 1);
    int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }
    try {
      WebKit.finishTask(task, url, MimeTypeUtil.of(path), ResourceUtil.read(path));
    } catch (ResourceNotFoundException e) {
      if (url.equals(this.loading)) {
        this.reportedFailure = url;
        this.emitLoad(LoadEvent.failed(url, e.getMessage()));
      }
      WebKit.failTask(task, e.getMessage());
    }
  }

  private static MemorySegment delegateStub(String method, int arguments) {
    MethodType type = MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class);
    for (int i = 0; i < arguments; i++) {
      type = type.appendParameterTypes(MemorySegment.class);
    }
    return NativeLibraries.upcall(
        MethodHandles.lookup(),
        MacWindow.class,
        method,
        type,
        switch (arguments) {
          case 1 -> Signatures.DELEGATE_1;
          case 2 -> Signatures.DELEGATE_2;
          default -> Signatures.DELEGATE_3;
        });
  }

  private static MacWindow windowOf(MemorySegment delegate) {
    return DELEGATES.get(delegate.address());
  }

  // --- LwjwaeDelegate methods; every one receives self and _cmd first, as Objective-C passes them
  // ---

  /**
   * The user asked to close the window. {@code NO} cancels the close; with {@link
   * dev.ivchenko.lwjwae.CloseAction#HIDE}, the window is ordered out instead.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static boolean onWindowShouldClose(
      MemorySegment self, MemorySegment command, MemorySegment sender) {
    try {
      MacWindow window = windowOf(self);
      if (window != null && window.hidesOnCloseRequest()) {
        AppKit.hide(window.window);
        return false;
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return true;
  }

  /**
   * {@code windowDidResize:}, {@code windowDidMove:}, {@code windowDidBecomeKey:}, {@code
   * windowDidResignKey:}, {@code windowDidMiniaturize:}, {@code windowDidDeminiaturize:}, {@code
   * windowDidEnterFullScreen:}, and {@code windowDidExitFullScreen:}: the window may have changed.
   * There's no notification of a zoom; the resize that comes with it reports it.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onWindowChanged(
      MemorySegment self, MemorySegment command, MemorySegment notification) {
    try {
      MacWindow window = windowOf(self);
      if (window != null) {
        window.windowChanged();
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
  private static void onWindowWillClose(
      MemorySegment self, MemorySegment command, MemorySegment notification) {
    try {
      MacWindow window = windowOf(self);
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
  private static void onDidStartProvisionalNavigation(
      MemorySegment self, MemorySegment command, MemorySegment webView, MemorySegment navigation) {
    try {
      MacWindow window = windowOf(self);
      if (window != null) {
        window.emitLoad(LoadEvent.of(LoadState.STARTED, window.url()));
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
  private static void onDidCommitNavigation(
      MemorySegment self, MemorySegment command, MemorySegment webView, MemorySegment navigation) {
    try {
      MacWindow window = windowOf(self);
      if (window != null) {
        window.emitLoad(LoadEvent.of(LoadState.COMMITTED, window.url()));
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
  private static void onDidFinishNavigation(
      MemorySegment self, MemorySegment command, MemorySegment webView, MemorySegment navigation) {
    try {
      MacWindow window = windowOf(self);
      if (window != null) {
        window.emitLoad(LoadEvent.of(LoadState.FINISHED, window.url()));
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
  private static void onDidFailNavigation(
      MemorySegment self,
      MemorySegment command,
      MemorySegment webView,
      MemorySegment navigation,
      MemorySegment error) {
    try {
      MacWindow window = windowOf(self);
      if (window != null) {
        window.handleLoadFailed(error);
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
  private static void onDidReceiveScriptMessage(
      MemorySegment self, MemorySegment command, MemorySegment controller, MemorySegment message) {
    try {
      MacWindow window = windowOf(self);
      if (window != null) {
        window.handleBridgeMessage(WebKit.messageBody(message));
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
  private static void onStartUrlSchemeTask(
      MemorySegment self, MemorySegment command, MemorySegment webView, MemorySegment task) {
    try {
      MacWindow window = windowOf(self);
      if (window != null && WebKit.taskPath(task).startsWith(RpcExchange.PATH_PREFIX)) {
        MacRpcExchange.start(window, task);
      } else if (window != null) {
        window.serveResource(task);
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Resources are answered in one step inside {@link #onStartUrlSchemeTask}, so only RPC calls have
   * anything to stop.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onStopUrlSchemeTask(
      MemorySegment self, MemorySegment command, MemorySegment webView, MemorySegment task) {
    try {
      MacRpcExchange.stop(task);
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onEvaluationComplete(
      MemorySegment block, MemorySegment result, MemorySegment error) {
    PendingEvaluation pending = PENDING_EVALUATIONS.unregister(ObjC.blockContext(block));
    if (pending == null) {
      return;
    }
    try {
      if (!ObjC.isNull(error)) {
        pending
            .result()
            .completeExceptionally(
                new ScriptEvaluationFailedException(Foundation.errorDescription(error)));
        return;
      }
      ScriptUtil.completeTagged(pending.result(), Foundation.string(result));
    } catch (Throwable t) {
      pending.result().completeExceptionally(t);
    }
  }
}
