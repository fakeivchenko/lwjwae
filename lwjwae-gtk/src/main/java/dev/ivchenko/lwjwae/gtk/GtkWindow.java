package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.WindowEdge;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.WindowSize;
import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.dialog.DialogCompletion;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.util.DecorationLayoutUtil;
import dev.ivchenko.lwjwae.gtk.binding.Gdk;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.gtk.binding.WebKit;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A window backed by GTK 3 and WebKitGTK 4.1, opened by a {@link GtkApplication}.
 *
 * <p>Instances are safe to use from any thread. Every call is forwarded to the shared GTK thread.
 * Native callbacks are static and dispatch through {@link CallbackRegistry}, so a single upcall
 * stub serves every window instead of one stub per instance.
 */
public class GtkWindow extends AbstractWindow {
  private static final CallbackRegistry<GtkWindow> WINDOWS = new CallbackRegistry<>();
  private static final Map<Long, GtkWindow> BY_WEB_VIEW = new ConcurrentHashMap<>();
  private static final CallbackRegistry<CompletableFuture<String>> PENDING_EVALUATIONS =
      new CallbackRegistry<>();

  private static final MemorySegment ON_DESTROY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
          "onDestroy",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.WIDGET_CALLBACK);
  private static final MemorySegment ON_DELETE_EVENT =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
          "onDeleteEvent",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.DELETE_EVENT_CALLBACK);
  private static final MemorySegment ON_WINDOW_EVENT =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
          "onWindowEvent",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.DELETE_EVENT_CALLBACK);
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
  private static final MemorySegment ON_CREATE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
          "onCreate",
          MethodType.methodType(
              MemorySegment.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.CREATE_CALLBACK);
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
  private volatile MemorySegment headerBar;

  // GTK keeps these without a getter, so the window remembers what it asked for.
  private volatile WindowSize minimumSize = WindowSize.NONE;
  private volatile WindowSize maximumSize = WindowSize.NONE;
  private volatile boolean alwaysOnTop;

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
    super(application, id, parameters);
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

    if (!parameters.decorated()) {
      // A title bar that never shows rather than gtk_window_set_decorated(FALSE): the window keeps
      // the frame that it draws itself, the shadow and the resize edges in it, and loses only the
      // bar. An empty one that shows would still take the height that the theme gives a title bar.
      MemorySegment none = Gtk.boxNew(Gtk.ORIENTATION_HORIZONTAL, 0);
      Gtk.widgetSetNoShowAll(none, true);
      Gtk.windowSetTitlebar(newWindow, none);
    } else if (!parameters.minimizable() || !parameters.maximizable()) {
      MemorySegment bar = this.titleBar(parameters);
      Gtk.windowSetTitlebar(newWindow, bar);
      this.headerBar = bar;
    }
    if (!parameters.closable()) {
      Gtk.windowSetDeletable(newWindow, false);
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

    Glib.signalConnect(newWindow, "delete-event", ON_DELETE_EVENT, userData);
    Glib.signalConnect(newWindow, "destroy", ON_DESTROY, userData);
    // Every change that a window event reports comes through one of these.
    for (String signal :
        List.of("configure-event", "window-state-event", "focus-in-event", "focus-out-event")) {
      Glib.signalConnect(newWindow, signal, ON_WINDOW_EVENT, userData);
    }
    Glib.signalConnect(newWebView, "load-changed", ON_LOAD_CHANGED, userData);
    Glib.signalConnect(newWebView, "load-failed", ON_LOAD_FAILED, userData);
    Glib.signalConnect(newWebView, "context-menu", ON_CONTEXT_MENU, userData);
    Glib.signalConnect(newWebView, "create", ON_CREATE, userData);

    this.window = newWindow;
    this.webView = newWebView;
    BY_WEB_VIEW.put(newWebView.address(), this);
    this.userContentManager = manager;
    this.installBridge();
  }

  /**
   * A title bar like the one that GTK draws by default, minus the buttons that the window may not
   * have. Neither the window manager of X11 nor GTK's own bar can drop the minimize button alone,
   * so a window that asks for that draws its bar itself, everywhere.
   */
  private MemorySegment titleBar(WindowParameters parameters) {
    MemorySegment bar = Gtk.headerBarNew();
    Gtk.headerBarSetTitle(bar, parameters.title());
    Gtk.headerBarSetShowCloseButton(bar, true);
    String layout = Glib.stringProperty(Gtk.settingsGetDefault(), "gtk-decoration-layout");
    Gtk.headerBarSetDecorationLayout(
        bar,
        DecorationLayoutUtil.without(layout, !parameters.minimizable(), !parameters.maximizable()));
    // The class of GTK's own bar, which is slimmer than a header bar of an application.
    Gtk.widgetAddCssClass(bar, "default-decoration");
    return bar;
  }

  @Override
  public String title() {
    return this.dispatcher().call(() -> Gtk.windowGetTitle(this.window()));
  }

  @Override
  public void title(String title) {
    this.dispatcher()
        .run(
            () -> {
              Gtk.windowSetTitle(this.window(), title);
              MemorySegment bar = this.headerBar;
              if (bar != null) {
                Gtk.headerBarSetTitle(bar, title);
              }
            });
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

  private void applySizeLimits() {
    Gtk.windowSetSizeLimits(
        this.window(),
        this.minimumSize.width(),
        this.minimumSize.height(),
        this.maximumSize.width(),
        this.maximumSize.height());
  }

  @Override
  public boolean isMinimized() {
    return this.dispatcher().call(() -> (this.state() & Gdk.STATE_ICONIFIED) != 0);
  }

  @Override
  public void minimize() {
    this.dispatcher().run(() -> Gtk.windowIconify(this.window()));
  }

  @Override
  public boolean isMaximized() {
    return this.dispatcher().call(() -> Gtk.isWindowMaximized(this.window()));
  }

  @Override
  public void maximize() {
    this.dispatcher().run(() -> Gtk.windowMaximize(this.window()));
  }

  @Override
  public void restore() {
    this.dispatcher()
        .run(
            () -> {
              Gtk.windowDeiconify(this.window());
              Gtk.windowUnmaximize(this.window());
            });
  }

  @Override
  public boolean isFullscreen() {
    return this.dispatcher().call(() -> (this.state() & Gdk.STATE_FULLSCREEN) != 0);
  }

  @Override
  public void fullscreen(boolean fullscreen) {
    this.dispatcher().run(() -> Gtk.windowSetFullscreen(this.window(), fullscreen));
  }

  @Override
  public boolean isAlwaysOnTop() {
    return this.alwaysOnTop;
  }

  @Override
  public void alwaysOnTop(boolean alwaysOnTop) {
    this.alwaysOnTop = alwaysOnTop;
    this.dispatcher().run(() -> Gtk.windowSetKeepAbove(this.window(), alwaysOnTop));
  }

  @Override
  public boolean isFocused() {
    return this.dispatcher().call(() -> Gtk.isWindowActive(this.window()));
  }

  /** {@code gtk_window_present} also deiconifies the window. */
  @Override
  public void focus() {
    this.show();
  }

  @Override
  protected void presentOpenDialog(
      OpenDialogParameters parameters, DialogCompletion<List<Path>> completion) {
    GtkDialogs.open(this.window(), parameters, completion);
  }

  @Override
  protected void presentSaveDialog(
      SaveDialogParameters parameters, DialogCompletion<Optional<Path>> completion) {
    GtkDialogs.save(this.window(), parameters, completion);
  }

  @Override
  protected void presentMessageDialog(
      MessageDialogParameters parameters, DialogCompletion<Boolean> completion) {
    GtkDialogs.message(this.window(), parameters, completion);
  }

  @Override
  protected void beginMove() {
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window();
              int[] pointer = Gdk.pressedPointerPosition(Gtk.widgetGetWindow(current));
              if (pointer != null) {
                Gtk.windowBeginMoveDrag(current, 1, pointer[0], pointer[1]);
              }
            });
  }

  @Override
  protected void beginResize(WindowEdge edge) {
    int gdkEdge =
        switch (edge) {
          case TOP -> Gdk.EDGE_NORTH;
          case BOTTOM -> Gdk.EDGE_SOUTH;
          case LEFT -> Gdk.EDGE_WEST;
          case RIGHT -> Gdk.EDGE_EAST;
          case TOP_LEFT -> Gdk.EDGE_NORTH_WEST;
          case TOP_RIGHT -> Gdk.EDGE_NORTH_EAST;
          case BOTTOM_LEFT -> Gdk.EDGE_SOUTH_WEST;
          case BOTTOM_RIGHT -> Gdk.EDGE_SOUTH_EAST;
        };
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window();
              if (!Gtk.isWindowResizable(current)) {
                return;
              }
              int[] pointer = Gdk.pressedPointerPosition(Gtk.widgetGetWindow(current));
              if (pointer != null) {
                Gtk.windowBeginResizeDrag(current, gdkEdge, 1, pointer[0], pointer[1]);
              }
            });
  }

  /** The {@code GdkWindowState} flags of the window. Call on the GTK thread. */
  private int state() {
    return Gdk.windowState(Gtk.widgetGetWindow(this.window()));
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
    this.dispatcher().run(() -> WebKit.loadHtml(this.webView(), html, this.resourceUrl("")));
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
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window();
              Gtk.widgetShowAll(current);
              Gtk.windowPresent(current);
            });
  }

  @Override
  public void requestClose() {
    this.dispatcher().run(() -> Gtk.windowClose(this.window()));
  }

  @Override
  public void hide() {
    this.dispatcher().run(() -> Gtk.widgetHide(this.window()));
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

  /** The window that shows {@code webView}, or {@code null}. */
  static GtkWindow ofWebView(MemorySegment webView) {
    return BY_WEB_VIEW.get(webView.address());
  }

  /** Serves an RPC request of the page of this window. */
  void rpc(RpcExchange exchange) {
    this.serveRpc(exchange);
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
    MemorySegment view = this.webView;
    if (view != null) {
      BY_WEB_VIEW.remove(view.address());
    }
    this.window = null;
    this.webView = null;
    this.userContentManager = null;
    this.headerBar = null;
    this.markClosed();
  }

  // --- signal handlers, bound by name from the upcall stubs above; signatures are GTK's ---

  /**
   * The user asked to close the window, from the title bar or the desktop. {@code TRUE} cancels the
   * close: a window that isn't closable refuses it, and with {@link
   * dev.ivchenko.lwjwae.CloseAction#HIDE}, the window is hidden instead.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static int onDeleteEvent(
      MemorySegment widget, MemorySegment event, MemorySegment userData) {
    try {
      GtkWindow window = WINDOWS.lookup(userData);
      if (window != null && window.refusesCloseRequest()) {
        return 1;
      }
      if (window != null && window.hidesOnCloseRequest()) {
        Gtk.widgetHide(widget);
        return 1;
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return 0;
  }

  /**
   * {@code configure-event}, {@code window-state-event}, {@code focus-in-event}, and {@code
   * focus-out-event}: the window may have changed. {@code FALSE} lets GTK handle the event too.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static int onWindowEvent(
      MemorySegment widget, MemorySegment event, MemorySegment userData) {
    try {
      GtkWindow window = WINDOWS.lookup(userData);
      if (window != null) {
        window.windowChanged();
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
   * The page asked for a new window, with {@code target="_blank"} or {@code window.open}. No web
   * view opens: the window decides where the URL goes, and {@code NULL} declines the request.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static MemorySegment onCreate(
      MemorySegment webView, MemorySegment action, MemorySegment userData) {
    try {
      GtkWindow window = WINDOWS.lookup(userData);
      if (window != null) {
        window.newWindowRequested(WebKit.navigationActionUri(action));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return MemorySegment.NULL;
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
