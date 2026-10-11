package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.WindowEdge;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.download.DownloadRequest;
import dev.ivchenko.lwjwae.download.DownloadState;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.Signatures;
import dev.ivchenko.lwjwae.glib.binding.WebKitGtk;
import dev.ivchenko.lwjwae.glib.util.WebKitEditingUtil;
import dev.ivchenko.lwjwae.menu.MenuRole;
import dev.ivchenko.lwjwae.permission.PermissionKind;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The half of a window of GTK 3 or GTK 4 that belongs to WebKitGTK: the web view and its signals,
 * the bridge, scripts, downloads, and permissions. A backend builds the window of its toolkit
 * around a web view, and hands both to {@link #adopt}; everything that the toolkit does differently
 * stays in the subclass.
 *
 * <p>Instances are safe to use from any thread. Every call is forwarded to the shared GTK thread.
 * Native callbacks are static and dispatch through a {@link CallbackRegistry}, so a single upcall
 * stub serves every window instead of one stub per instance. A process loads one toolkit, so the
 * registries and the stubs of this class serve the windows of one backend.
 */
public abstract class WebKitGtkWindow extends AbstractWindow {
  private static final CallbackRegistry<WebKitGtkWindow> WINDOWS = new CallbackRegistry<>();
  private static final Map<Long, WebKitGtkWindow> BY_WEB_VIEW = new ConcurrentHashMap<>();
  private static final CallbackRegistry<CompletableFuture<String>> PENDING_EVALUATIONS =
      new CallbackRegistry<>();

  /** The objects that emit {@code download-started}, each connected once, for every window. */
  private static final Set<Long> DOWNLOAD_SOURCES = ConcurrentHashMap.newKeySet();

  private static final MemorySegment ON_DESTROY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onDestroy",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.VOID_POINTER_POINTER);
  private static final MemorySegment ON_LOAD_CHANGED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onLoadChanged",
          MethodType.methodType(void.class, MemorySegment.class, int.class, MemorySegment.class),
          Signatures.WEBKIT_LOAD_CHANGED_CALLBACK);
  private static final MemorySegment ON_LOAD_FAILED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onLoadFailed",
          MethodType.methodType(
              int.class,
              MemorySegment.class,
              int.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class),
          Signatures.WEBKIT_LOAD_FAILED_CALLBACK);
  private static final MemorySegment ON_CREATE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onCreate",
          MethodType.methodType(
              MemorySegment.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.POINTER_POINTER_POINTER_POINTER);
  private static final MemorySegment ON_PERMISSION_REQUEST =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onPermissionRequest",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.INT_POINTER_POINTER_POINTER);
  private static final MemorySegment ON_EVALUATION_READY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onEvaluationReady",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.G_ASYNC_READY_CALLBACK);
  private static final MemorySegment ON_BRIDGE_MESSAGE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onBridgeMessage",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.VOID_POINTER_POINTER_POINTER);
  private static final MemorySegment ON_DOWNLOAD_STARTED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onDownloadStarted",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.VOID_POINTER_POINTER_POINTER);
  private static final MemorySegment ON_DECIDE_DESTINATION =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onDecideDestination",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.INT_POINTER_POINTER_POINTER);
  private static final MemorySegment ON_DOWNLOAD_RECEIVED_DATA =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onDownloadReceivedData",
          MethodType.methodType(void.class, MemorySegment.class, long.class, MemorySegment.class),
          Signatures.WEBKIT_DOWNLOAD_RECEIVED_DATA_CALLBACK);
  private static final MemorySegment ON_DOWNLOAD_FAILED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onDownloadFailed",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.VOID_POINTER_POINTER_POINTER);
  private static final MemorySegment ON_DOWNLOAD_FINISHED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onDownloadFinished",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.VOID_POINTER_POINTER);
  private static final MemorySegment ON_WAKE_UP =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitGtkWindow.class,
          "onWakeUp",
          MethodType.methodType(int.class, MemorySegment.class),
          Signatures.G_SOURCE_FUNC);

  /**
   * The binding of the WebKitGTK that the process loaded, for the callbacks that no window answers
   * for: every window of a process has the same one.
   */
  private static volatile WebKitGtk loadedWebKit;

  private final WebKitGtk webKit;
  private final long callbackId;
  private final boolean frameless;

  /**
   * The ID of each download of the page that started, by the address of its {@code WebKitDownload}.
   */
  private final Map<Long, Long> downloadIds = new ConcurrentHashMap<>();

  private volatile MemorySegment window;
  private volatile MemorySegment webView;
  private volatile MemorySegment userContentManager;

  /**
   * Records the window in the registry of the callbacks; the subclass creates the native window
   * afterwards and hands it to {@link #adopt}.
   *
   * <p>Suppressed warnings: {@code this-escape}: the registry only keeps the window for the
   * callbacks of its widgets, which come no sooner than the subclass created them.
   *
   * @param webKit The binding of the WebKitGTK of the backend.
   */
  @SuppressWarnings("this-escape")
  protected WebKitGtkWindow(
      AbstractApplication application, long id, WindowParameters parameters, WebKitGtk webKit) {
    super(application, id, parameters);
    loadedWebKit = webKit;
    this.webKit = webKit;
    this.frameless = !parameters.decorated() && parameters.transparent();
    this.callbackId = WINDOWS.register(this);
  }

  /**
   * Takes this window out of the registry of the callbacks: the subclass failed to create the
   * native window, and nothing will call back for it.
   */
  protected final void forgetFailedWindow() {
    WINDOWS.unregister(this.callbackId);
  }

  /** The window that shows {@code webView}, or {@code null}. */
  public static WebKitGtkWindow ofWebView(MemorySegment webView) {
    return BY_WEB_VIEW.get(webView.address());
  }

  /**
   * The window of {@code type} that a callback carries as its data, or {@code null} for one that is
   * gone.
   */
  protected static <W extends WebKitGtkWindow> W windowOf(MemorySegment userData, Class<W> type) {
    WebKitGtkWindow window = WINDOWS.lookup(userData);
    return type.isInstance(window) ? type.cast(window) : null;
  }

  /** The data that the callbacks of this window carry, which {@link #windowOf} reads. */
  protected final MemorySegment callbackData() {
    return CallbackRegistry.userData(this.callbackId);
  }

  /** The binding of the WebKitGTK of the backend. */
  protected final WebKitGtk webKit() {
    return this.webKit;
  }

  /**
   * Sets what every web view of the library has: the user agent of the library, the camera and the
   * microphone for the permission handler, and, for the tests of the library, fake ones. Call on
   * the GTK thread, before {@link #adopt}.
   */
  protected final void configureWebView(MemorySegment newWebView) {
    this.webKit.setUserAgent(newWebView, this.userAgent(this.webKit.userAgent(newWebView)));
    this.webKit.setMediaStreamEnabled(newWebView, true);
    // Set by the tests of the library only, which run where no camera is.
    if (Boolean.getBoolean("lwjwae.mockCaptureDevices")) {
      this.webKit.setMockCaptureDevicesEnabled(newWebView, true);
    }
  }

  /**
   * Hears the messages of the page on {@code manager}, before the subclass registers the channel: a
   * message that came between the two would find no handler.
   */
  protected final void listenToBridge(MemorySegment manager) {
    Glib.signalConnect(
        manager,
        "script-message-received::" + BridgeProtocol.CHANNEL,
        ON_BRIDGE_MESSAGE,
        this.callbackData());
  }

  /**
   * Takes over the native window and its web view, which the subclass built: connects the signals
   * that this class handles, hears the downloads of the view, and installs the bridge. Call once,
   * on the GTK thread, at the end of building the window.
   *
   * @param manager The {@code WebKitUserContentManager} of the view.
   */
  protected final void adopt(
      MemorySegment newWindow, MemorySegment newWebView, MemorySegment manager) {
    MemorySegment userData = this.callbackData();
    Glib.signalConnect(newWindow, "destroy", ON_DESTROY, userData);
    Glib.signalConnect(newWebView, "load-changed", ON_LOAD_CHANGED, userData);
    Glib.signalConnect(newWebView, "load-failed", ON_LOAD_FAILED, userData);
    Glib.signalConnect(newWebView, "create", ON_CREATE, userData);
    Glib.signalConnect(newWebView, "permission-request", ON_PERMISSION_REQUEST, userData);

    this.window = newWindow;
    this.webView = newWebView;
    this.userContentManager = manager;
    BY_WEB_VIEW.put(newWebView.address(), this);
    MemorySegment downloadSource = this.downloadSource(newWebView);
    if (DOWNLOAD_SOURCES.add(downloadSource.address())) {
      Glib.signalConnect(
          downloadSource, "download-started", ON_DOWNLOAD_STARTED, MemorySegment.NULL);
    }
    this.installBridge();
  }

  /** The object that emits {@code download-started} for {@code webView}. */
  protected abstract MemorySegment downloadSource(MemorySegment webView);

  /** Sets where {@code download} writes, in the form that the WebKitGTK of the backend takes. */
  protected abstract void downloadDestination(MemorySegment download, Path destination);

  /**
   * Hands a message that the page posted to the bridge, or to whatever else of the backend posts
   * through the channel. Runs on the GTK thread.
   *
   * @param message The argument of {@code script-message-received}, as the version of WebKitGTK
   *     sends it.
   */
  protected abstract void receiveBridgeMessage(MemorySegment message);

  /**
   * Lets go of the widgets that the subclass holds. Runs on the GTK thread, once, when the native
   * window is destroyed, before the window counts as closed.
   */
  protected abstract void releaseWidgets();

  /** Every edge of a transparent window without a title bar, which has no frame to resize from. */
  @Override
  protected List<WindowEdge> pageResizeEdges() {
    return this.frameless ? List.of(WindowEdge.values()) : List.of();
  }

  @Override
  protected void presentZoom(double factor) {
    this.webKit.setZoomLevel(this.webView, factor);
  }

  @Override
  protected double currentZoom() {
    return this.webKit.zoomLevel(this.webView);
  }

  @Override
  protected void performEditing(MenuRole role) {
    MemorySegment view = this.webView;
    if (view != null) {
      this.webKit.executeEditingCommand(view, WebKitEditingUtil.command(role));
    }
  }

  @Override
  protected final boolean currentDevTools() {
    return this.webKit.isDeveloperExtrasEnabled(this.view());
  }

  @Override
  protected final void presentDevTools(boolean enabled) {
    this.webKit.setDeveloperExtrasEnabled(this.view(), enabled);
  }

  @Override
  public void navigate(String url) {
    Objects.requireNonNull(url, "url");
    this.dispatcher().run(() -> this.webKit.loadUri(this.view(), url));
  }

  @Override
  public void html(String html) {
    Objects.requireNonNull(html, "html");
    this.dispatcher().run(() -> this.webKit.loadHtml(this.view(), html, this.resourceUrl("")));
  }

  @Override
  public String url() {
    return this.dispatcher().call(() -> this.webKit.uri(this.view()));
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
                this.webKit.evaluateJavascript(
                    this.view(),
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
    this.dispatcher().run(() -> this.webKit.addUserScript(this.userContentManager(), script));
  }

  @Override
  protected String bridgeTransportScript() {
    return "(message) => window.webkit.messageHandlers.%s.postMessage(message)"
        .formatted(BridgeProtocol.CHANNEL);
  }

  /** Serves an RPC request of the page of this window. */
  public final void rpc(RpcExchange exchange) {
    this.serveRpc(exchange);
  }

  /** The native {@code GtkWindow}. Call on the GTK thread. */
  public final MemorySegment window() {
    return this.alive(this.window);
  }

  /** The native {@code GtkWindow}, or {@code null} once it's destroyed. */
  protected final MemorySegment windowIfAny() {
    return this.window;
  }

  /** The web view. Call on the GTK thread. */
  protected final MemorySegment view() {
    return this.alive(this.webView);
  }

  /** The web view, or {@code null} once it's destroyed. */
  protected final MemorySegment viewIfAny() {
    return this.webView;
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

  private void destroyed() {
    MemorySegment view = this.webView;
    if (view != null) {
      BY_WEB_VIEW.remove(view.address());
    }
    this.window = null;
    this.webView = null;
    this.userContentManager = null;
    this.releaseWidgets();
    this.markClosed();
  }

  /**
   * Runs the main loop on this thread until {@code future} completes, and returns its value. Call
   * on the GTK thread, from a callback that must answer before it returns.
   */
  private <T> T awaitOnMainLoop(CompletableFuture<T> future) {
    // A completion on another thread wakes the loop, which would otherwise wait for an event.
    future.whenComplete((_, _) -> Glib.idleAdd(ON_WAKE_UP, MemorySegment.NULL));
    while (!future.isDone()) {
      Glib.mainContextIteration(true);
    }
    return future.join();
  }

  /** The URL of {@code download}, or {@code about:blank} for one that isn't a URI. */
  private URI downloadUrl(MemorySegment download) {
    try {
      return URI.create(this.webKit.downloadUri(download));
    } catch (IllegalArgumentException | NullPointerException _) {
      return URI.create("about:blank");
    }
  }

  // --- signal handlers, bound by name from the upcall stubs above; signatures are GTK's ---

  /**
   * The native window is gone: what the window holds goes with it, and the window counts as closed.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onDestroy(MemorySegment widget, MemorySegment userData) {
    try {
      WebKitGtkWindow window = WINDOWS.unregister(userData);
      if (window != null) {
        window.destroyed();
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Suppressed warnings: as for {@link #onDestroy}.
   *
   * @param loadEvent A {@code WebKitLoadEvent}.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onLoadChanged(MemorySegment webView, int loadEvent, MemorySegment userData) {
    try {
      WebKitGtkWindow window = WINDOWS.lookup(userData);
      if (window == null) {
        return;
      }

      LoadState state =
          switch (loadEvent) {
            case WebKitGtk.LOAD_STARTED -> LoadState.STARTED;
            case WebKitGtk.LOAD_REDIRECTED -> LoadState.REDIRECTED;
            case WebKitGtk.LOAD_COMMITTED -> LoadState.COMMITTED;
            case WebKitGtk.LOAD_FINISHED -> LoadState.FINISHED;
            default -> null;
          };
      if (state != null) {
        window.emitLoad(LoadEvent.of(state, window.webKit.uri(webView)));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /** Suppressed warnings: as for {@link #onDestroy}. */
  @SuppressWarnings({"unused", "resource"})
  private static int onLoadFailed(
      MemorySegment webView,
      int loadEvent,
      MemorySegment failingUri,
      MemorySegment error,
      MemorySegment userData) {
    try {
      WebKitGtkWindow window = WINDOWS.lookup(userData);
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
   * The page asked for a new window, with {@code target="_blank"} or {@code window.open}. No web
   * view opens: the window decides where the URL goes, and {@code NULL} declines the request.
   *
   * <p>Suppressed warnings: as for {@link #onDestroy}.
   */
  @SuppressWarnings({"unused", "resource"})
  private static MemorySegment onCreate(
      MemorySegment webView, MemorySegment action, MemorySegment userData) {
    try {
      WebKitGtkWindow window = WINDOWS.lookup(userData);
      if (window != null) {
        window.newWindowRequested(window.webKit.navigationActionUri(action));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return MemorySegment.NULL;
  }

  /**
   * The page asks for a permission, and WebKit waits for the answer. Only the camera and the
   * microphone reach the handler; the position, notifications, and a capture of the screen are
   * denied here, so that the engine never shows a prompt of its own.
   *
   * <p>Suppressed warnings: as for {@link #onDestroy}.
   */
  @SuppressWarnings({"unused", "resource"})
  private static int onPermissionRequest(
      MemorySegment webView, MemorySegment request, MemorySegment userData) {
    WebKitGtk webKit = loadedWebKit;
    boolean granted = false;
    try {
      WebKitGtkWindow window = WINDOWS.lookup(userData);
      if (window != null
          && webKit.isUserMediaRequest(request)
          && !webKit.isForDisplayDevice(request)) {
        List<PermissionKind> kinds = new ArrayList<>();
        if (webKit.isForVideoDevice(request)) {
          kinds.add(PermissionKind.CAMERA);
        }
        if (webKit.isForAudioDevice(request)) {
          kinds.add(PermissionKind.MICROPHONE);
        }
        granted =
            window.permissionRequested(webKit.uri(webView), kinds.toArray(PermissionKind[]::new));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    try {
      webKit.answerPermissionRequest(request, granted);
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
      pending.complete(loadedWebKit.evaluateJavascriptFinish(source, result));
    } catch (Throwable t) {
      pending.completeExceptionally(t);
    }
  }

  /** Suppressed warnings: as for {@link #onDestroy}. */
  @SuppressWarnings({"unused", "resource"})
  private static void onBridgeMessage(
      MemorySegment manager, MemorySegment message, MemorySegment userData) {
    try {
      WebKitGtkWindow window = WINDOWS.lookup(userData);
      if (window != null) {
        window.receiveBridgeMessage(message);
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * A download started in some web view; it belongs to the window of that web view, which hears its
   * steps from here on. WebKit emits this on the object that every window shares, so the window is
   * found by its web view.
   *
   * <p>Suppressed warnings: as for {@link #onDestroy}.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onDownloadStarted(
      MemorySegment source, MemorySegment download, MemorySegment userData) {
    try {
      MemorySegment view = loadedWebKit.downloadWebView(download);
      WebKitGtkWindow window =
          view.equals(MemorySegment.NULL) ? null : BY_WEB_VIEW.get(view.address());
      if (window == null || window.isClosed()) {
        return;
      }
      MemorySegment data = window.callbackData();
      Glib.signalConnect(download, "decide-destination", ON_DECIDE_DESTINATION, data);
      Glib.signalConnect(download, "received-data", ON_DOWNLOAD_RECEIVED_DATA, data);
      Glib.signalConnect(download, "failed", ON_DOWNLOAD_FAILED, data);
      Glib.signalConnect(download, "finished", ON_DOWNLOAD_FINISHED, data);
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * WebKit asks where the download goes, and takes the answer when the handler returns. The handler
   * of the application runs on a virtual thread meanwhile, while the main loop keeps running here,
   * so the UI stays alive and a dialog that the handler shows works.
   *
   * <p>Suppressed warnings: as for {@link #onDestroy}.
   */
  @SuppressWarnings({"unused", "resource"})
  private static int onDecideDestination(
      MemorySegment download, MemorySegment suggestedFileName, MemorySegment userData) {
    WebKitGtk webKit = loadedWebKit;
    try {
      WebKitGtkWindow window = WINDOWS.lookup(userData);
      if (window == null) {
        webKit.downloadCancel(download);
        return 1;
      }
      URI url = window.downloadUrl(download);
      long totalBytes = webKit.downloadContentLength(download);
      DownloadRequest request =
          new DownloadRequest(
              url,
              NativeLibraries.string(suggestedFileName),
              webKit.downloadMimeType(download),
              totalBytes);
      Optional<Path> path = window.awaitOnMainLoop(window.downloadRequested(request));
      if (path.isEmpty() || window.isClosed()) {
        webKit.downloadCancel(download);
        return 1;
      }
      window.downloadDestination(download, path.get());
      Glib.ref(download);
      long address = download.address();
      long id =
          window.downloadStarted(
              url,
              path.get(),
              totalBytes,
              () -> {
                if (window.downloadIds.containsKey(address)) {
                  webKit.downloadCancel(download);
                }
              });
      window.downloadIds.put(address, id);
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      webKit.downloadCancel(download);
    }
    return 1;
  }

  /** Suppressed warnings: as for {@link #onDestroy}. */
  @SuppressWarnings({"unused", "resource"})
  private static void onDownloadReceivedData(
      MemorySegment download, long dataLength, MemorySegment userData) {
    try {
      WebKitGtkWindow window = WINDOWS.lookup(userData);
      Long id = window == null ? null : window.downloadIds.get(download.address());
      if (id != null) {
        window.downloadProgressed(
            id,
            window.webKit.downloadReceivedLength(download),
            window.webKit.downloadContentLength(download));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * The download stopped, canceled or on an error. {@code finished} follows, and finds the download
   * ended already.
   *
   * <p>Suppressed warnings: as for {@link #onDestroy}.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onDownloadFailed(
      MemorySegment download, MemorySegment error, MemorySegment userData) {
    try {
      WebKitGtkWindow window = WINDOWS.lookup(userData);
      Long id = window == null ? null : window.downloadIds.get(download.address());
      if (id != null) {
        boolean canceled = Glib.errorCode(error) == WebKitGtk.DOWNLOAD_ERROR_CANCELLED_BY_USER;
        window.downloadEnded(
            id,
            canceled ? DownloadState.CANCELED : DownloadState.FAILED,
            canceled ? "" : Glib.errorMessage(error));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * The download is over, after {@code failed} too; a download that didn't fail completed.
   *
   * <p>Suppressed warnings: as for {@link #onDestroy}.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onDownloadFinished(MemorySegment download, MemorySegment userData) {
    try {
      WebKitGtkWindow window = WINDOWS.lookup(userData);
      Long id = window == null ? null : window.downloadIds.remove(download.address());
      if (id != null) {
        window.downloadEnded(id, DownloadState.COMPLETED, "");
        Glib.unref(download);
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Does nothing, once: an idle callback that wakes the main loop.
   *
   * <p>Suppressed warnings: {@code unused}: reached only through the upcall stub.
   */
  @SuppressWarnings("unused")
  private static int onWakeUp(MemorySegment userData) {
    return Glib.SOURCE_REMOVE;
  }
}
