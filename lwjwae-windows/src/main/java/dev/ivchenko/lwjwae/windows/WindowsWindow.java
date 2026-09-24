package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.WindowSize;
import dev.ivchenko.lwjwae.bridge.RpcMessageChannel;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.util.MimeTypeUtil;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import dev.ivchenko.lwjwae.util.ScriptUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.ComCallback;
import dev.ivchenko.lwjwae.windows.binding.ComEvent;
import dev.ivchenko.lwjwae.windows.binding.Shlwapi;
import dev.ivchenko.lwjwae.windows.binding.Signatures;
import dev.ivchenko.lwjwae.windows.binding.User32;
import dev.ivchenko.lwjwae.windows.binding.WebView2;
import dev.ivchenko.lwjwae.windows.binding.WebView2EventRegistration;
import dev.ivchenko.lwjwae.windows.binding.Wide;
import dev.ivchenko.lwjwae.windows.exception.ComCallFailedException;
import dev.ivchenko.lwjwae.windows.util.JsonStringUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A window backed by a Win32 window and WebView2, opened by a {@link WindowsApplication}.
 *
 * <p>Instances are safe to use from any thread. Every call is forwarded to the shared UI thread,
 * which is also the COM apartment that every WebView2 callback arrives on. The window procedure is
 * static and finds its backend through the {@code GWLP_USERDATA} slot of the window, so one upcall
 * stub serves every window.
 *
 * <p>WebView2 has no custom URI schemes, so classpath resources are served under {@code
 * http://app.localhost/}. Requests to that host are intercepted before they reach the network and
 * answered from the JAR file, and {@code .localhost} is a secure context in Chromium.
 */
public class WindowsWindow extends AbstractWindow {
  private volatile boolean sharedBuffers = true;

  // --- window state that Windows keeps no getter for ---
  private volatile WindowSize minimumSize = WindowSize.NONE;
  private volatile WindowSize maximumSize = WindowSize.NONE;
  private volatile boolean fullscreen;
  private byte[] placementBeforeFullscreen;
  private long styleBeforeFullscreen;

  /**
   * What {@link #show()} shows the window as. {@code ShowWindow} would show a hidden window that is
   * minimized or maximized, which the other backends don't do, so a hidden window keeps the state
   * until it's shown.
   */
  private volatile int showCommand = User32.SW_SHOW;

  private static final String WINDOW_CLASS = "lwjwae";
  private static final String RESOURCE_ORIGIN = "http://app.localhost/";
  private static final Duration CREATION_TIMEOUT = Duration.ofMinutes(1);

  private static final CallbackRegistry<WindowsWindow> WINDOWS = new CallbackRegistry<>();

  private static final MemorySegment WINDOW_PROC =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WindowsWindow.class,
          "windowProc",
          MethodType.methodType(long.class, MemorySegment.class, int.class, long.class, long.class),
          Signatures.LONG_POINTER_INT_LONG_LONG);

  private static volatile boolean windowClassRegistered;

  private final WindowsApplication application;
  private final long callbackId;
  private final CompletableFuture<Void> ready = new CompletableFuture<>();

  private volatile MemorySegment hwnd;
  private volatile MemorySegment controller;
  private volatile MemorySegment webView;

  /**
   * Creates the Win32 window and the WebView2 controller inside it, and returns when both exist.
   * The window is hidden until {@link #show()}. On the UI thread, messages keep flowing while the
   * controller is created; see {@link WindowsDispatcher#await}.
   *
   * <p>Suppressed warnings: {@code resource}: on the failure path, {@code unregister} hands back
   * this window, which looks like an unclosed resource. It is not: {@code destroyQuietly} tears
   * down what was built, and the exception tells the caller that nothing was opened.
   *
   * @throws IllegalStateException If WebView2 doesn't answer within a minute.
   * @throws ComCallFailedException If WebView2 refuses to create the controller.
   */
  @SuppressWarnings("resource")
  WindowsWindow(WindowsApplication application, long id, WindowParameters parameters) {
    super(application, id);
    this.application = application;
    this.callbackId = WINDOWS.register(this);
    try {
      this.dispatcher().run(() -> this.createWindow(parameters));
      ((WindowsDispatcher) this.dispatcher()).await(this.ready, CREATION_TIMEOUT);
    } catch (Exception e) {
      WINDOWS.unregister(this.callbackId);
      this.dispatcher().run(this::destroyQuietly);
      throw e instanceof RuntimeException runtime
          ? runtime
          : new IllegalStateException("WebView2 creation failed", e);
    }
  }

  /** Creates the Win32 window and starts the asynchronous WebView2 setup. Runs on the UI thread. */
  private void createWindow(WindowParameters parameters) {
    registerWindowClass();
    boolean placed = parameters.hasPosition() && !parameters.centered();
    MemorySegment window =
        User32.createWindow(
            WINDOW_CLASS,
            parameters.title(),
            placed ? parameters.x() : User32.CW_USEDEFAULT,
            placed ? parameters.y() : User32.CW_USEDEFAULT,
            parameters.width(),
            parameters.height(),
            parameters.alwaysOnTop());
    User32.userData(window, this.callbackId);
    this.hwnd = window;
    User32.resizeClient(window, parameters.width(), parameters.height());
    if (parameters.centered()) {
      centerWindow(window);
    }

    MemorySegment handler =
        ComCallback.completion(WebView2.IID_CONTROLLER_COMPLETED, this::onControllerCreated);
    WebView2.createController(this.application.environment(), window, handler);
    Com.release(handler);
  }

  @Override
  public String title() {
    return this.dispatcher().call(() -> User32.title(this.window()));
  }

  @Override
  public void title(String title) {
    this.dispatcher().run(() -> User32.setTitle(this.window(), title));
  }

  @Override
  public int width() {
    return this.dispatcher().call(() -> User32.clientSize(this.window())[0]);
  }

  @Override
  public int height() {
    return this.dispatcher().call(() -> User32.clientSize(this.window())[1]);
  }

  @Override
  public void size(int width, int height) {
    this.dispatcher().run(() -> User32.resizeClient(this.window(), width, height));
  }

  @Override
  public WindowPosition position() {
    return this.dispatcher()
        .call(
            () -> {
              int[] frame = User32.windowRect(this.window());
              return new WindowPosition(frame[0], frame[1]);
            });
  }

  @Override
  public void position(int x, int y) {
    this.dispatcher().run(() -> User32.move(this.window(), x, y));
  }

  @Override
  public void center() {
    this.dispatcher().run(() -> centerWindow(this.window()));
  }

  /** Puts the frame in the middle of the work area of the monitor that holds the window. */
  private static void centerWindow(MemorySegment hwnd) {
    int[] area = User32.workArea(hwnd);
    int[] frame = User32.windowRect(hwnd);
    int width = frame[2] - frame[0];
    int height = frame[3] - frame[1];
    User32.move(
        hwnd,
        area[0] + (area[2] - area[0] - width) / 2,
        area[1] + (area[3] - area[1] - height) / 2);
  }

  @Override
  public boolean isResizable() {
    return this.dispatcher().call(() -> (User32.style(this.window()) & User32.WS_THICKFRAME) != 0);
  }

  @Override
  public void resizable(boolean resizable) {
    this.dispatcher()
        .run(
            () -> {
              long style = User32.style(this.window());
              long resizing = User32.WS_THICKFRAME | User32.WS_MAXIMIZEBOX;
              User32.style(this.window(), resizable ? style | resizing : style & ~resizing);
            });
  }

  @Override
  public WindowSize minimumSize() {
    return this.minimumSize;
  }

  @Override
  public void minimumSize(int width, int height) {
    this.minimumSize = new WindowSize(width, height);
    this.dispatcher().run(this::enforceSizeLimits);
  }

  @Override
  public WindowSize maximumSize() {
    return this.maximumSize;
  }

  @Override
  public void maximumSize(int width, int height) {
    this.maximumSize = new WindowSize(width, height);
    this.dispatcher().run(this::enforceSizeLimits);
  }

  /** Resizes the window to its own size, which runs it through {@code WM_GETMINMAXINFO}. */
  private void enforceSizeLimits() {
    MemorySegment hwnd = this.window();
    int[] client = User32.clientSize(hwnd);
    User32.resizeClient(hwnd, client[0], client[1]);
  }

  /** Answers {@code WM_GETMINMAXINFO}: the limits of the client area, as frame sizes. */
  private void writeSizeLimits(MemorySegment hwnd, long minMaxInfo) {
    WindowSize minimum = this.minimumSize;
    WindowSize maximum = this.maximumSize;
    User32.sizeLimits(
        minMaxInfo,
        minimum.equals(WindowSize.NONE) ? null : this.frameLimit(hwnd, minimum, 0),
        maximum.equals(WindowSize.NONE) ? null : this.frameLimit(hwnd, maximum, Short.MAX_VALUE));
  }

  /** The frame size around {@code limit}, with {@code unlimited} for a dimension without one. */
  private int[] frameLimit(MemorySegment hwnd, WindowSize limit, int unlimited) {
    int[] frame = User32.frameSize(hwnd, Math.max(limit.width(), 1), Math.max(limit.height(), 1));
    return new int[] {
      limit.width() > 0 ? frame[0] : unlimited, limit.height() > 0 ? frame[1] : unlimited
    };
  }

  @Override
  public boolean isMinimized() {
    return this.dispatcher()
        .call(
            () ->
                User32.isVisible(this.window())
                    ? User32.isMinimized(this.window())
                    : this.showCommand == User32.SW_MINIMIZE);
  }

  @Override
  public void minimize() {
    this.dispatcher().run(() -> this.showAs(User32.SW_MINIMIZE));
  }

  @Override
  public boolean isMaximized() {
    return this.dispatcher()
        .call(
            () ->
                User32.isVisible(this.window())
                    ? User32.isMaximized(this.window())
                    : this.showCommand == User32.SW_MAXIMIZE);
  }

  @Override
  public void maximize() {
    this.dispatcher().run(() -> this.showAs(User32.SW_MAXIMIZE));
  }

  /** {@code SW_RESTORE} brings a minimized window back to maximized if it was, hence the second. */
  @Override
  public void restore() {
    this.dispatcher()
        .run(
            () -> {
              MemorySegment hwnd = this.window();
              if (!User32.isVisible(hwnd)) {
                this.showCommand = User32.SW_SHOW;
                return;
              }
              User32.showWindow(hwnd, User32.SW_RESTORE);
              if (User32.isMaximized(hwnd)) {
                User32.showWindow(hwnd, User32.SW_RESTORE);
              }
            });
  }

  /** Applies {@code command} now to a visible window, or when a hidden one is shown. */
  private void showAs(int command) {
    MemorySegment hwnd = this.window();
    if (User32.isVisible(hwnd)) {
      User32.showWindow(hwnd, command);
    } else {
      this.showCommand = command;
    }
  }

  @Override
  public boolean isFullscreen() {
    this.checkOpen();
    return this.fullscreen;
  }

  /**
   * Windows has no full screen state; a window is in full screen when it has no frame and covers
   * its monitor. The style and the placement from before are kept, and put back when it ends.
   */
  @Override
  public void fullscreen(boolean fullscreen) {
    this.dispatcher()
        .run(
            () -> {
              if (fullscreen == this.fullscreen) {
                return;
              }
              MemorySegment hwnd = this.window();
              this.fullscreen = fullscreen;
              if (fullscreen) {
                this.placementBeforeFullscreen = User32.placement(hwnd);
                this.styleBeforeFullscreen = User32.style(hwnd);
                User32.style(hwnd, this.styleBeforeFullscreen & ~User32.WS_OVERLAPPEDWINDOW);
                User32.bounds(hwnd, User32.monitorRect(hwnd));
              } else {
                User32.style(hwnd, this.styleBeforeFullscreen);
                User32.placement(hwnd, this.placementBeforeFullscreen);
              }
            });
  }

  @Override
  public boolean isAlwaysOnTop() {
    return this.dispatcher().call(() -> User32.isTopmost(this.window()));
  }

  @Override
  public void alwaysOnTop(boolean alwaysOnTop) {
    this.dispatcher().run(() -> User32.topmost(this.window(), alwaysOnTop));
  }

  @Override
  public boolean isFocused() {
    return this.dispatcher().call(() -> User32.isForeground(this.window()));
  }

  @Override
  public void focus() {
    this.show();
    this.dispatcher()
        .run(
            () -> {
              if (User32.isMinimized(this.window())) {
                User32.showWindow(this.window(), User32.SW_RESTORE);
              }
              User32.bringToFront(this.window());
            });
  }

  @Override
  public boolean isDevToolsEnabled() {
    return this.dispatcher().call(() -> WebView2.isDevToolsEnabled(this.view()));
  }

  @Override
  public void devToolsEnabled(boolean devToolsEnabled) {
    this.dispatcher().run(() -> WebView2.setDevToolsEnabled(this.view(), devToolsEnabled));
  }

  @Override
  public void navigate(String url) {
    Objects.requireNonNull(url, "url");
    this.dispatcher().run(() -> WebView2.navigate(this.view(), url));
  }

  @Override
  public String url() {
    return this.dispatcher()
        .call(
            () -> {
              String source = WebView2.source(this.view());
              return source == null || source.isEmpty() || "about:blank".equals(source)
                  ? null
                  : source;
            });
  }

  @Override
  public void html(String html) {
    Objects.requireNonNull(html, "html");
    this.dispatcher().run(() -> WebView2.navigateToString(this.view(), html));
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code ExecuteScript} reports a thrown exception as a {@code null} result instead of a
   * failure, and serializes everything as JSON. Therefore, the script is wrapped. The wrapper
   * evaluates the text of the caller in the global scope, catches exceptions, and returns a single
   * string tagged with its outcome. The completion decodes the string back into a value or a {@link
   * ScriptEvaluationFailedException}.
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
                MemorySegment handler =
                    ComCallback.completion(
                        WebView2.IID_EXECUTE_SCRIPT_COMPLETED,
                        (hresult, json) -> completeEvaluation(result, hresult, Wide.read(json)));
                WebView2.executeScript(this.view(), wrapped, handler);
                Com.release(handler);
              } catch (Throwable t) {
                result.completeExceptionally(t);
              }
            });
    return result;
  }

  @Override
  protected void injectOnDocumentStart(String script) {
    this.dispatcher()
        .run(
            () -> {
              MemorySegment handler =
                  ComCallback.completion(WebView2.IID_ADD_SCRIPT_COMPLETED, (_, _) -> {});
              WebView2.addScriptToExecuteOnDocumentCreated(this.view(), script, handler);
              Com.release(handler);
            });
  }

  @Override
  protected String bridgeTransportScript() {
    return "(message) => window.chrome.webview.postMessage(message)";
  }

  @Override
  protected String resourceUrl(String path) {
    return RESOURCE_ORIGIN + (path.startsWith("/") ? path.substring(1) : path);
  }

  @Override
  public void show() {
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window();
              User32.showWindow(
                  current, User32.isVisible(current) ? User32.SW_SHOW : this.showCommand);
              this.showCommand = User32.SW_SHOW;
              WebView2.setVisible(this.controller, true);
              User32.setForeground(current);
            });
  }

  @Override
  public void requestClose() {
    this.dispatcher().run(() -> User32.requestClose(this.window()));
  }

  @Override
  public void hide() {
    this.dispatcher().run(this::hideNow);
  }

  @Override
  public boolean isVisible() {
    return this.dispatcher().call(() -> User32.isVisible(this.window()));
  }

  /**
   * Hides the window and tells WebView2 that its view is hidden, which lets it throttle the page.
   * Runs on the UI thread.
   */
  private void hideNow() {
    WebView2.setVisible(this.controller, false);
    User32.hide(this.window());
  }

  @Override
  public void close() {
    if (this.isClosed()) {
      return;
    }
    // DestroyWindow sends WM_DESTROY synchronously, which is what completes the close.
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.hwnd;
              if (!this.isClosed() && current != null) {
                User32.destroy(current);
              }
            });
  }

  /**
   * Runs the load listeners once the WebView2 handler that saw the event has returned.
   *
   * <p>WebView2 raises no event and no completion while one of its handlers is on the stack, even
   * when that handler pumps messages. A listener that opens a window waits for a completion in a
   * nested loop, so it can't run inside the handler: it would wait out the whole creation timeout.
   * Posted, it runs from the task queue on the same thread, in the same order.
   */
  private void reportLoad(LoadEvent event) {
    this.dispatcher()
        .post(
            () -> {
              if (!this.isClosed()) {
                this.emitLoad(event);
              }
            });
  }

  private void onControllerCreated(int hresult, MemorySegment createdController) {
    if (hresult < 0) {
      this.ready.completeExceptionally(
          new ComCallFailedException("WebView2 controller creation", hresult));
      return;
    }
    try {
      Com.addRef(createdController);
      this.controller = createdController;
      this.webView = WebView2.coreWebView2(createdController);
      this.fitWebView();
      WebView2.setDevToolsEnabled(this.webView, false);

      this.subscribe(
          WebView2::onNavigationStarting,
          WebView2.IID_NAVIGATION_STARTING,
          (_, arguments) ->
              this.reportLoad(
                  LoadEvent.of(LoadState.STARTED, WebView2.navigationStartingUri(arguments))));
      this.subscribe(
          WebView2::onContentLoading,
          WebView2.IID_CONTENT_LOADING,
          (sender, _) ->
              this.reportLoad(LoadEvent.of(LoadState.COMMITTED, WebView2.source(sender))));
      this.subscribe(
          WebView2::onNavigationCompleted,
          WebView2.IID_NAVIGATION_COMPLETED,
          (sender, arguments) -> {
            String uri = WebView2.source(sender);
            this.reportLoad(
                WebView2.isNavigationSuccessful(arguments)
                    ? LoadEvent.of(LoadState.FINISHED, uri)
                    : LoadEvent.failed(uri, WebView2.navigationErrorStatus(arguments)));
          });
      this.subscribe(
          WebView2::onWebMessageReceived,
          WebView2.IID_WEB_MESSAGE_RECEIVED,
          (_, arguments) -> {
            String message = WebView2.webMessageAsString(arguments);
            if (message != null) {
              this.handleBridgeMessage(message);
            }
          });
      WebView2.addWebResourceRequestedFilter(this.webView, RESOURCE_ORIGIN + "*");
      this.subscribe(
          WebView2::onWebResourceRequested,
          WebView2.IID_WEB_RESOURCE_REQUESTED,
          (_, arguments) -> this.serveResource(arguments));

      this.installBridge();
      this.ready.complete(null);
    } catch (RuntimeException e) {
      this.ready.completeExceptionally(e);
    }
  }

  private void subscribe(
      WebView2EventRegistration registration, MemorySegment iid, ComEvent handler) {
    MemorySegment callback = ComCallback.event(iid, handler);
    registration.add(this.webView, callback);
    Com.release(callback);
  }

  /**
   * WebView2 reads a custom response to its end before the page sees any of it, and doesn't tell
   * the host when the page gives up on a request, so every call, {@code lwjwae.call} included, goes
   * through web messages here.
   */
  @Override
  protected String rpcTransportScript() {
    return "{base:null,webview2:true}";
  }

  /** Web messages and shared buffers; see {@link WindowsMessageChannel}. */
  @Override
  protected RpcMessageChannel rpcMessageChannel() {
    return new WindowsMessageChannel(this);
  }

  /** Posts {@code message} to the page with {@code PostWebMessageAsString}, on the UI thread. */
  CompletableFuture<?> postWebMessage(String message) {
    CompletableFuture<Void> posted = new CompletableFuture<>();
    this.dispatcher()
        .post(
            () -> {
              try {
                MemorySegment view = this.webView;
                if (view != null) {
                  WebView2.postWebMessageAsString(view, message);
                }
                posted.complete(null);
              } catch (Throwable t) {
                posted.completeExceptionally(t);
              }
            });
    return posted;
  }

  /**
   * Posts {@code data} as a shared buffer, which reaches the page as an {@code ArrayBuffer} with no
   * encoding, or {@code fallback} where the runtime has no shared buffers, older than 114. On the
   * UI thread.
   */
  CompletableFuture<?> postSharedBuffer(
      byte[] data, String additionalDataAsJson, Supplier<String> fallback) {
    if (!this.sharedBuffers) {
      return this.postWebMessage(fallback.get());
    }
    CompletableFuture<Void> posted = new CompletableFuture<>();
    this.dispatcher()
        .post(
            () -> {
              try {
                MemorySegment view = this.webView;
                if (view != null) {
                  try {
                    WebView2.postSharedBuffer(
                        this.application.environment(), view, data, additionalDataAsJson);
                  } catch (ComCallFailedException _) {
                    this.sharedBuffers = false;
                    WebView2.postWebMessageAsString(view, fallback.get());
                  }
                }
                posted.complete(null);
              } catch (Throwable t) {
                posted.completeExceptionally(t);
              }
            });
    return posted;
  }

  /** Answers one {@code http://app.localhost/} request out of the classpath. */
  private void serveResource(MemorySegment arguments) {
    String uri = WebView2.requestedUri(arguments);
    String path = uri.startsWith(RESOURCE_ORIGIN) ? uri.substring(RESOURCE_ORIGIN.length()) : uri;
    int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }

    MemorySegment response;
    try {
      byte[] content = ResourceUtil.read(path);
      MemorySegment stream = Shlwapi.memoryStream(content);
      try {
        response =
            WebView2.createResponse(
                this.application.environment(),
                stream,
                200,
                "OK",
                "Content-Type: " + MimeTypeUtil.of(path));
      } finally {
        Com.release(stream);
      }
    } catch (ResourceNotFoundException e) {
      response =
          WebView2.createResponse(
              this.application.environment(), MemorySegment.NULL, 404, "Not Found", "");
      if (WebView2.isDocumentRequest(arguments)) {
        this.reportLoad(LoadEvent.failed(uri, e.getMessage()));
      }
    }
    try {
      WebView2.respond(arguments, response);
    } finally {
      Com.release(response);
    }
  }

  private static void completeEvaluation(
      CompletableFuture<String> result, int hresult, String json) {
    if (hresult < 0) {
      result.completeExceptionally(new ComCallFailedException("ExecuteScript", hresult));
      return;
    }
    ScriptUtil.completeTagged(result, JsonStringUtil.decode(json));
  }

  private void fitWebView() {
    int[] size = User32.clientSize(this.hwnd);
    WebView2.setBounds(this.controller, size[0], size[1]);
  }

  private MemorySegment window() {
    return this.alive(this.hwnd);
  }

  private MemorySegment view() {
    return this.alive(this.webView);
  }

  private MemorySegment alive(MemorySegment handle) {
    this.checkOpen();
    if (handle == null) {
      throw new IllegalStateException("The window is closed");
    }
    return handle;
  }

  private void handleDestroyed() {
    final MemorySegment closingController = this.controller;
    final MemorySegment closingView = this.webView;
    this.hwnd = null;
    this.webView = null;
    this.controller = null;
    if (closingController != null) {
      try {
        WebView2.close(closingController);
      } catch (RuntimeException e) {
        ThrowableUtil.report(e);
      }
      Com.release(closingController);
    }
    Com.release(closingView);
    // Last: the application can stop waiting in run() only once the native window is gone.
    this.markClosed();
  }

  private void destroyQuietly() {
    MemorySegment current = this.hwnd;
    if (current != null && !this.isClosed()) {
      User32.destroy(current);
    }
  }

  private static synchronized void registerWindowClass() {
    if (windowClassRegistered) {
      return;
    }
    User32.registerClass(WINDOW_CLASS, WINDOW_PROC);
    windowClassRegistered = true;
  }

  /**
   * The {@code WNDPROC} of every window that this backend creates. It must never let a throwable
   * unwind into user32.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static long windowProc(
      MemorySegment hwnd, int message, long wordParameter, long longParameter) {
    try {
      WindowsWindow window = WINDOWS.lookup(User32.userData(hwnd));
      if (window != null) {
        if (message == User32.WM_GETMINMAXINFO && !window.fullscreen) {
          window.writeSizeLimits(hwnd, longParameter);
          return 0;
        } else if (message == User32.WM_SIZE && window.controller != null) {
          window.fitWebView();
          window.windowChanged();
        } else if (message == User32.WM_MOVE || message == User32.WM_ACTIVATE) {
          window.windowChanged();
        } else if (message == User32.WM_CLOSE && window.hidesOnCloseRequest()) {
          // Not passed on: DefWindowProc would destroy the window.
          window.hideNow();
          return 0;
        } else if (message == User32.WM_DESTROY) {
          WINDOWS.unregister(window.callbackId);
          window.handleDestroyed();
        }
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return User32.defWindowProc(hwnd, message, wordParameter, longParameter);
  }
}
