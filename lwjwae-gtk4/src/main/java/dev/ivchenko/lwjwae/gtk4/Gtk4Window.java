package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.WindowEdge;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.WindowSize;
import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.dialog.DialogCompletion;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.PortalShortcuts;
import dev.ivchenko.lwjwae.glib.WebKitGtkWindow;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.util.DecorationLayoutUtil;
import dev.ivchenko.lwjwae.gtk4.binding.Gtk;
import dev.ivchenko.lwjwae.gtk4.binding.Signatures;
import dev.ivchenko.lwjwae.gtk4.binding.WebKit;
import dev.ivchenko.lwjwae.menu.MenuCommands;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
public class Gtk4Window extends WebKitGtkWindow {
  private static final CallbackRegistry<CompletableFuture<List<Path>>> FILE_READS =
      new CallbackRegistry<>();

  /** What the script of the page posts when files are dropped on it, before where. */
  private static final String FILE_DROP_MESSAGE = "lwjwae:files";

  /**
   * Tells the host where files were dropped on the page, which it can't learn from GTK: the drop
   * target of WebKit takes the drop, and the page hides the paths. It runs first, in the capture
   * phase, so that a page that stops the event still lets Java hear of the drop. The paths come
   * from the drag itself, see {@link #onDragEnter}, so a page that posts this without a drag finds
   * nothing to report.
   */
  private static final String FILE_DROP_SCRIPT =
      """
      window.addEventListener("drop", (event) => {
        const types = event.dataTransfer ? Array.from(event.dataTransfer.types) : [];
        if (!types.includes("Files") && !types.includes("text/uri-list")) return;
        window.webkit.messageHandlers.%s.postMessage(
            "%s%s" + Math.round(event.clientX) + "%s" + Math.round(event.clientY));
      }, true);
      """
          .formatted(
              BridgeProtocol.CHANNEL,
              FILE_DROP_MESSAGE,
              BridgeProtocol.SEPARATOR,
              BridgeProtocol.SEPARATOR);

  private static final MemorySegment ON_REALIZE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onRealize",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.WIDGET_CALLBACK);
  private static final MemorySegment ON_STATE_NOTIFY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onStateNotify",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.NOTIFY_CALLBACK);
  private static final MemorySegment ON_LAYOUT =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onLayout",
          MethodType.methodType(
              void.class, MemorySegment.class, int.class, int.class, MemorySegment.class),
          Signatures.LAYOUT_CALLBACK);
  private static final MemorySegment ON_CLOSE_REQUEST =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onCloseRequest",
          MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class),
          Signatures.CLOSE_REQUEST_CALLBACK);
  private static final MemorySegment ON_DRAG_ENTER =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onDragEnter",
          MethodType.methodType(
              void.class, MemorySegment.class, double.class, double.class, MemorySegment.class),
          Signatures.DROP_MOTION_CALLBACK);
  private static final MemorySegment ON_FILES_READ =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Window.class,
          "onFilesRead",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.G_ASYNC_READY_CALLBACK);
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

  private volatile WindowSize minimumSize = WindowSize.NONE;

  /** The box that holds the menu bar, if any, above the web view. */
  private volatile MemorySegment box;

  private volatile MemorySegment menuBarWidget;
  private volatile Gtk4MenuBuilder menuBarBuilder;
  private volatile MemorySegment shortcutController;

  /** The files of the drag over the web view, once read, or {@code null} without one. */
  private volatile CompletableFuture<List<Path>> draggedFiles;

  /**
   * Creates the native window on the GTK thread and returns when it exists. The window is hidden
   * until {@link #show()}.
   */
  Gtk4Window(Gtk4Application application, long id, WindowParameters parameters) {
    super(application, id, parameters, WebKit.SHARED);
    try {
      this.dispatcher().run(() -> this.createWindow(parameters));
    } catch (RuntimeException | Error e) {
      this.forgetFailedWindow();
      throw e;
    }
  }

  /** Builds the native widgets. Runs on the GTK thread, once, from the constructor. */
  private void createWindow(WindowParameters parameters) {
    MemorySegment newWindow = Gtk.windowNew();
    Gtk.windowSetTitle(newWindow, parameters.title());
    Gtk.windowSetDefaultSize(newWindow, parameters.size().width(), parameters.size().height());
    if (parameters.parent() instanceof Gtk4Window parent) {
      Gtk.windowSetTransientFor(newWindow, parent.window());
      Gtk.windowJoinGroupOf(newWindow, parent.window());
      Gtk.windowSetModal(newWindow, parameters.modal());
    }
    if (!parameters.decorated() && parameters.transparent()) {
      // No frame at all: its shadow and its edge would outline the whole window around the shape
      // that the page draws.
      Gtk.windowSetDecorated(newWindow, false);
    } else if (!parameters.decorated()) {
      // A title bar that never shows rather than gtk_window_set_decorated(FALSE): the window keeps
      // the frame that it draws itself, the shadow and the resize edges in it, and loses only the
      // bar.
      MemorySegment none = Gtk.boxNew(Gtk.ORIENTATION_HORIZONTAL, 0);
      Gtk.widgetSetVisible(none, false);
      Gtk.windowSetTitlebar(newWindow, none);
    } else if (!parameters.minimizable() || !parameters.maximizable()) {
      Gtk.windowSetTitlebar(newWindow, Gtk4Window.titleBar(parameters));
    }
    if (!parameters.closable()) {
      Gtk.windowSetDeletable(newWindow, false);
    }
    if (parameters.transparent()) {
      Gtk.windowClearBackground(newWindow);
    }

    MemorySegment newWebView = WebKit.webViewNew();
    if (parameters.transparent()) {
      WebKit.setTransparentBackground(newWebView);
    }
    this.configureWebView(newWebView);
    MemorySegment manager = WebKit.userContentManager(newWebView);
    this.listenToBridge(manager);
    if (!WebKit.registerScriptMessageHandler(manager, BridgeProtocol.CHANNEL)) {
      throw new IllegalStateException(
          "Message handler '" + BridgeProtocol.CHANNEL + "' already registered");
    }

    MemorySegment newBox = Gtk.boxNew(Gtk.ORIENTATION_VERTICAL, 0);
    Gtk.widgetSetVexpand(newWebView, true);
    Gtk.boxAppend(newBox, newWebView);
    Gtk.windowSetChild(newWindow, newBox);

    MemorySegment userData = this.callbackData();
    Glib.signalConnect(newWindow, "close-request", ON_CLOSE_REQUEST, userData);
    Glib.signalConnect(newWindow, "realize", ON_REALIZE, userData);
    Glib.signalConnect(newWebView, "context-menu", ON_CONTEXT_MENU, userData);
    Gtk.addDropMotionController(newWebView, ON_DRAG_ENTER, userData);

    this.box = newBox;
    this.adopt(newWindow, newWebView, manager);
    this.injectOnDocumentStart(FILE_DROP_SCRIPT);
  }

  @Override
  protected MemorySegment downloadSource(MemorySegment webView) {
    return WebKit.downloadSource(webView);
  }

  @Override
  protected void downloadDestination(MemorySegment download, Path destination) {
    WebKit.downloadSetDestination(download, destination);
  }

  /** The bridge, or the place of a drop that {@link #FILE_DROP_SCRIPT} reports. */
  @Override
  protected void receiveBridgeMessage(MemorySegment message) {
    String text = WebKit.scriptMessageText(message);
    if (text.startsWith(FILE_DROP_MESSAGE)) {
      this.fileDropMessage(text);
    } else {
      this.handleBridgeMessage(text);
    }
  }

  /**
   * A title bar like the one that GTK draws by default, minus the buttons that the window may not
   * have: GTK's own bar can't drop the minimize button alone. It shows the title of the window.
   */
  private static MemorySegment titleBar(WindowParameters parameters) {
    MemorySegment bar = Gtk.headerBarNew();
    String layout = Glib.stringProperty(Gtk.settingsGetDefault(), "gtk-decoration-layout");
    Gtk.headerBarSetDecorationLayout(
        bar,
        DecorationLayoutUtil.without(layout, !parameters.minimizable(), !parameters.maximizable()));
    // The class of GTK's own bar, which is slimmer than a header bar of an application.
    Gtk.widgetAddCssClass(bar, "default-decoration");
    return bar;
  }

  /**
   * Does nothing: GTK 4 has no call that takes an image for a window, and the desktop takes the
   * icon from the {@code .desktop} file of the application.
   */
  @Override
  protected void presentIcon(byte[] png) {}

  @Override
  public String title() {
    return this.dispatcher().call(() -> Gtk.windowGetTitle(this.window()));
  }

  @Override
  public void title(String title) {
    this.dispatcher().run(() -> Gtk.windowSetTitle(this.window(), title));
  }

  @Override
  public WindowSize size() {
    return this.dispatcher()
        .call(
            () -> {
              int[] size = this.contentSize();
              return new WindowSize(size[0], size[1]);
            });
  }

  @Override
  public void size(int width, int height) {
    this.dispatcher().run(() -> Gtk.windowSetDefaultSize(this.window(), width, height));
  }

  /**
   * The size that the web view was allocated. GTK 4 keeps the default size of a window as the size
   * it goes back to, so it stays put while the window is maximized or in full screen; only a window
   * that was never shown, and has no allocation yet, answers with it.
   */
  private int[] contentSize() {
    int[] allocated = Gtk.widgetSize(this.view());
    return allocated[0] > 0 ? allocated : Gtk.windowGetDefaultSize(this.window());
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
  public Screen screen() {
    return this.dispatcher().call(() -> Gtk4Screens.at(Gtk.windowSurface(this.window())));
  }

  @Override
  public void center() {
    this.checkOpen();
  }

  @Override
  protected void presentOpenDialog(
      OpenDialogParameters parameters, DialogCompletion<List<Path>> completion) {
    Gtk4Dialogs.open(this.window(), parameters, completion);
  }

  @Override
  protected void presentSaveDialog(
      SaveDialogParameters parameters, DialogCompletion<Optional<Path>> completion) {
    Gtk4Dialogs.save(this.window(), parameters, completion);
  }

  /**
   * A {@code GtkPopoverMenuBar} above the web view, with its actions under {@code menu} in the
   * window and its keys in a controller of the window, which sees them before the page.
   */
  @Override
  protected void presentMenuBar(MenuCommands commands) {
    this.dropMenuBar();
    MemorySegment current = this.windowIfAny();
    if (current == null || commands.isEmpty()) {
      return;
    }
    Gtk4MenuBuilder builder =
        new Gtk4MenuBuilder(commands, id -> this.menuItemPicked(commands, id), "menu");
    Gtk.widgetInsertActionGroup(current, "menu", builder.group());
    MemorySegment bar = Gtk.popoverMenuBarNewFromModel(builder.model());
    Gtk.boxPrepend(this.box, bar);
    this.shortcutController = Gtk.addShortcutController(current, builder.shortcuts());
    this.menuBarWidget = bar;
    this.menuBarBuilder = builder;
  }

  /** Takes the menu bar, its actions, and its keys away. Runs on the GTK thread. */
  private void dropMenuBar() {
    MemorySegment bar = this.menuBarWidget;
    this.menuBarWidget = null;
    if (bar != null && this.box != null) {
      Gtk.boxRemove(this.box, bar);
    }
    MemorySegment current = this.windowIfAny();
    MemorySegment controller = this.shortcutController;
    this.shortcutController = null;
    if (controller != null && current != null) {
      Gtk.widgetRemoveController(current, controller);
    }
    Gtk4MenuBuilder builder = this.menuBarBuilder;
    this.menuBarBuilder = null;
    if (builder != null) {
      if (current != null) {
        Gtk.widgetInsertActionGroup(current, "menu", MemorySegment.NULL);
      }
      builder.release();
    }
  }

  /**
   * A {@code GtkPopoverMenu} on the web view, with its actions under {@code context} in the window
   * while it's open. The answer for no pick waits for the next turn of the loop after {@code
   * closed}, so an entry picked answers first.
   */
  @Override
  protected void presentContextMenu(
      MenuCommands commands, WindowPosition place, DialogCompletion<Integer> picked) {
    MemorySegment current = this.windowIfAny();
    MemorySegment view = this.viewIfAny();
    if (current == null || view == null) {
      picked.complete(0);
      return;
    }
    Gtk4MenuBuilder builder = new Gtk4MenuBuilder(commands, picked::complete, "context");
    Gtk.widgetInsertActionGroup(current, "context", builder.group());
    MemorySegment popover = Gtk.popoverMenuNewFromModel(builder.model());
    builder.onClosed(
        popover,
        () ->
            this.dispatcher()
                .post(
                    () -> {
                      picked.complete(0);
                      Gtk.widgetUnparent(popover);
                      MemorySegment still = this.windowIfAny();
                      if (still != null) {
                        Gtk.widgetInsertActionGroup(still, "context", MemorySegment.NULL);
                      }
                      builder.release();
                    }));
    picked.onCancel(() -> Gtk.popoverPopdown(popover));
    int[] at = place != null ? new int[] {place.x(), place.y()} : Gtk.pointerIn(current, view);
    Gtk.popupAt(popover, view, at[0], at[1]);
  }

  @Override
  protected void presentMessageDialog(
      MessageDialogParameters parameters, DialogCompletion<Boolean> completion) {
    Gtk4Dialogs.message(this.window(), parameters, completion);
  }

  @Override
  protected void beginMove() {
    this.dispatcher().run(() -> Gtk.toplevelBeginMove(Gtk.windowSurface(this.window())));
  }

  @Override
  protected void beginResize(WindowEdge edge) {
    int gdkEdge =
        switch (edge) {
          case TOP -> Gtk.EDGE_NORTH;
          case BOTTOM -> Gtk.EDGE_SOUTH;
          case LEFT -> Gtk.EDGE_WEST;
          case RIGHT -> Gtk.EDGE_EAST;
          case TOP_LEFT -> Gtk.EDGE_NORTH_WEST;
          case TOP_RIGHT -> Gtk.EDGE_NORTH_EAST;
          case BOTTOM_LEFT -> Gtk.EDGE_SOUTH_WEST;
          case BOTTOM_RIGHT -> Gtk.EDGE_SOUTH_EAST;
        };
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window();
              if (Gtk.isWindowResizable(current)) {
                Gtk.toplevelBeginResize(Gtk.windowSurface(current), gdkEdge);
              }
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

  /**
   * GTK 4 has no minimum size of a window, only of a widget, so the limit goes on the web view: the
   * window can't get smaller than what it holds.
   */
  @Override
  public WindowSize minimumSize() {
    return this.minimumSize;
  }

  @Override
  public void minimumSize(int width, int height) {
    this.minimumSize = new WindowSize(width, height);
    this.dispatcher()
        .run(
            () ->
                Gtk.widgetSetSizeRequest(
                    this.view(), width > 0 ? width : -1, height > 0 ? height : -1));
  }

  /** Always {@link WindowSize#NONE}: GTK 4 has no maximum size. */
  @Override
  public WindowSize maximumSize() {
    this.checkOpen();
    return WindowSize.NONE;
  }

  /** Does nothing: GTK 4 has no maximum size. */
  @Override
  public void maximumSize(int width, int height) {
    this.checkOpen();
  }

  @Override
  public boolean isMinimized() {
    return this.dispatcher().call(() -> Gtk.isWindowMinimized(this.window()));
  }

  @Override
  public void minimize() {
    this.dispatcher().run(() -> Gtk.windowSetMinimized(this.window(), true));
  }

  @Override
  public boolean isMaximized() {
    return this.dispatcher().call(() -> Gtk.isWindowMaximized(this.window()));
  }

  @Override
  public void maximize() {
    this.dispatcher().run(() -> Gtk.windowSetMaximized(this.window(), true));
  }

  @Override
  public void restore() {
    this.dispatcher()
        .run(
            () -> {
              Gtk.windowSetMinimized(this.window(), false);
              Gtk.windowSetMaximized(this.window(), false);
            });
  }

  @Override
  public boolean isFullscreen() {
    return this.dispatcher().call(() -> Gtk.isWindowFullscreen(this.window()));
  }

  @Override
  public void fullscreen(boolean fullscreen) {
    this.dispatcher().run(() -> Gtk.windowSetFullscreen(this.window(), fullscreen));
  }

  /** Always {@code false}: GTK 4 has no way to keep a window above the others. */
  @Override
  public boolean isAlwaysOnTop() {
    this.checkOpen();
    return false;
  }

  /** Does nothing: GTK 4 has no way to keep a window above the others. */
  @Override
  public void alwaysOnTop(boolean alwaysOnTop) {
    this.checkOpen();
  }

  @Override
  public boolean isFocused() {
    return this.dispatcher().call(() -> Gtk.isWindowActive(this.window()));
  }

  /** {@code gtk_window_present} also unminimizes the window. */
  @Override
  public void focus() {
    this.show();
  }

  @Override
  public void show() {
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window();
              String token = PortalShortcuts.takeActivationToken();
              if (token != null) {
                Gtk.windowSetStartupId(current, token);
              }
              Gtk.windowPresent(current);
            });
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
              MemorySegment current = this.windowIfAny();
              if (!this.isClosed() && current != null) {
                Gtk.windowDestroy(current);
              }
            });
  }

  @Override
  protected void releaseWidgets() {
    Gtk4MenuBuilder builder = this.menuBarBuilder;
    this.menuBarBuilder = null;
    if (builder != null) {
      builder.release();
    }
    this.menuBarWidget = null;
    this.shortcutController = null;
    this.box = null;
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
  private static int onCloseRequest(MemorySegment widget, MemorySegment userData) {
    try {
      Gtk4Window window = WebKitGtkWindow.windowOf(userData, Gtk4Window.class);
      if (window != null && window.refusesCloseRequest()) {
        return 1;
      }
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
   * The window is realized, and has a surface: GTK 4 reports the state of a window, minimized,
   * maximized, full screen, and focus, only on its {@code GdkToplevel}, and its size on the {@code
   * layout} of the surface.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onRealize(MemorySegment widget, MemorySegment userData) {
    try {
      Gtk4Window window = WebKitGtkWindow.windowOf(userData, Gtk4Window.class);
      if (window == null) {
        return;
      }
      MemorySegment surface = Gtk.windowSurface(widget);
      Glib.signalConnect(surface, "notify::state", ON_STATE_NOTIFY, userData);
      Glib.signalConnect(surface, "layout", ON_LAYOUT, userData);
      window.windowChanged();
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * The state of the surface changed.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name.
   */
  @SuppressWarnings("unused")
  private static void onStateNotify(
      MemorySegment surface, MemorySegment property, MemorySegment userData) {
    Gtk4Window.changed(userData);
  }

  /**
   * The surface has a new size.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name.
   */
  @SuppressWarnings("unused")
  private static void onLayout(
      MemorySegment surface, int width, int height, MemorySegment userData) {
    Gtk4Window.changed(userData);
  }

  /**
   * Tells the window found by {@code userData}, if it's still open, that it may have changed.
   *
   * <p>Suppressed warnings: {@code resource}: the window is only borrowed.
   */
  @SuppressWarnings("resource")
  private static void changed(MemorySegment userData) {
    try {
      Gtk4Window window = WebKitGtkWindow.windowOf(userData, Gtk4Window.class);
      if (window != null) {
        window.windowChanged();
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
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
      Gtk4Window window = WebKitGtkWindow.windowOf(userData, Gtk4Window.class);
      if (window != null && WebKit.SHARED.isDeveloperExtrasEnabled(webView)) {
        return 0;
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return 1;
  }

  /**
   * A drop of files on the page, which the script of the page posted with where it happened: the
   * window reports the files of the drag that it read, once they are read. Without a drag that
   * offered files, there is nothing to report.
   */
  private void fileDropMessage(String message) {
    String[] fields = message.split(BridgeProtocol.SEPARATOR);
    CompletableFuture<List<Path>> files = this.draggedFiles;
    this.draggedFiles = null;
    if (files == null || fields.length != 3) {
      return;
    }
    int x = Integer.parseInt(fields[1]);
    int y = Integer.parseInt(fields[2]);
    files.whenComplete(
        (paths, failure) -> {
          if (failure != null) {
            ThrowableUtil.report(failure);
          } else {
            this.filesDropped(paths, x, y);
          }
        });
  }

  /**
   * A drag comes over the web view. If it offers files, their paths are read now, while the source
   * holds them, for the drop that the page reports, see {@link #fileDropMessage}; the drop target
   * of WebKit reads them again for itself.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name. {@code resource}: the window is only borrowed.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onDragEnter(
      MemorySegment controller, double x, double y, MemorySegment userData) {
    try {
      Gtk4Window window = WebKitGtkWindow.windowOf(userData, Gtk4Window.class);
      if (window == null) {
        return;
      }
      MemorySegment drop = Gtk.dropOfFiles(controller);
      if (drop.equals(MemorySegment.NULL)) {
        window.draggedFiles = null;
        return;
      }
      CompletableFuture<List<Path>> files = new CompletableFuture<>();
      window.draggedFiles = files;
      long id = FILE_READS.register(files);
      Gtk.dropReadFilesAsync(drop, ON_FILES_READ, CallbackRegistry.userData(id));
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * The files of a drag are read.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name.
   */
  @SuppressWarnings("unused")
  private static void onFilesRead(
      MemorySegment source, MemorySegment result, MemorySegment userData) {
    CompletableFuture<List<Path>> files = FILE_READS.unregister(userData);
    if (files == null) {
      return;
    }
    try {
      files.complete(Gtk.dropReadFilesFinish(source, result).stream().map(Path::of).toList());
    } catch (Throwable t) {
      files.completeExceptionally(t);
    }
  }
}
