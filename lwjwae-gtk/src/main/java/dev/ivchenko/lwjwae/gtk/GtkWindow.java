package dev.ivchenko.lwjwae.gtk;

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
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.PortalShortcuts;
import dev.ivchenko.lwjwae.glib.WebKitGtkWindow;
import dev.ivchenko.lwjwae.glib.binding.GdkPixbuf;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.util.DecorationLayoutUtil;
import dev.ivchenko.lwjwae.gtk.binding.Gdk;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.gtk.binding.WebKit;
import dev.ivchenko.lwjwae.menu.MenuCommands;
import dev.ivchenko.lwjwae.util.FileDropUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A window backed by GTK 3 and WebKitGTK 4.1, opened by a {@link GtkApplication}: the {@code
 * GtkWindow} around the web view, whose WebKitGTK half is {@link WebKitGtkWindow}'s.
 *
 * <p>Instances are safe to use from any thread. Every call is forwarded to the shared GTK thread.
 * Native callbacks are static and dispatch through the registry of {@link WebKitGtkWindow}, so a
 * single upcall stub serves every window instead of one stub per instance.
 */
public class GtkWindow extends WebKitGtkWindow {

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
  private static final MemorySegment ON_TICK =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
          "onTick",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.TICK_CALLBACK);
  private static final MemorySegment ON_DRAG_MOTION =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
          "onDragMotion",
          MethodType.methodType(
              int.class,
              MemorySegment.class,
              MemorySegment.class,
              int.class,
              int.class,
              int.class,
              MemorySegment.class),
          Signatures.DRAG_MOTION_CALLBACK);
  private static final MemorySegment ON_DRAG_DATA_RECEIVED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkWindow.class,
          "onDragDataReceived",
          MethodType.methodType(
              void.class,
              MemorySegment.class,
              MemorySegment.class,
              int.class,
              int.class,
              MemorySegment.class,
              int.class,
              int.class,
              MemorySegment.class),
          Signatures.DRAG_DATA_RECEIVED_CALLBACK);
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

  private static final String FRAMELESS_CSS =
      "decoration, window { box-shadow: none; border: none; border-radius: 0; margin: 0;"
          + " background: none; }";

  /** Where the pointer was at the last motion of a drag over the web view. */
  private volatile int dragX;

  private volatile int dragY;
  private volatile MemorySegment headerBar;

  /** The box that holds the menu bar, if any, above the web view. */
  private volatile MemorySegment box;

  /** The group that the keys of the menu bar go to, which the window owns. */
  private volatile MemorySegment accelGroup;

  private volatile MemorySegment menuBarWidget;
  private volatile GtkMenuBuilder menuBarBuilder;

  // GTK keeps these without a getter, so the window remembers what it asked for.
  private volatile WindowSize minimumSize = WindowSize.NONE;
  private volatile WindowSize maximumSize = WindowSize.NONE;
  private volatile boolean alwaysOnTop;

  /** The frames to wait before the unmaximize of {@link #restore()} goes again. GTK thread only. */
  private int unmaximizeFrames;

  /**
   * Creates the native window on the GTK thread and returns when it exists. The window is hidden
   * until {@link #show()}.
   */
  GtkWindow(GtkApplication application, long id, WindowParameters parameters) {
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
    MemorySegment newWindow = Gtk.windowNew(Gtk.WINDOW_TOPLEVEL);
    Gtk.windowSetTitle(newWindow, parameters.title());
    Gtk.windowSetDefaultSize(newWindow, parameters.size().width(), parameters.size().height());
    if (parameters.centered()) {
      Gtk.windowSetPosition(newWindow, Gtk.WIN_POS_CENTER);
    } else if (parameters.hasPosition()) {
      Gtk.windowMove(newWindow, parameters.position().x(), parameters.position().y());
    } else if (parameters.parent() != null) {
      Gtk.windowSetPosition(newWindow, Gtk.WIN_POS_CENTER_ON_PARENT);
    }
    if (parameters.parent() instanceof GtkWindow parent) {
      Gtk.windowSetTransientFor(newWindow, parent.window());
      Gtk.windowJoinGroupOf(newWindow, parent.window());
      Gtk.windowSetModal(newWindow, parameters.modal());
    }

    if (!parameters.decorated()) {
      // A title bar that never shows rather than gtk_window_set_decorated(FALSE): the window keeps
      // the frame that it draws itself, the shadow and the resize edges in it, and loses only the
      // bar. An empty one that shows would still take the height that the theme gives a title bar.
      MemorySegment none = Gtk.boxNew(Gtk.ORIENTATION_HORIZONTAL, 0);
      Gtk.widgetSetNoShowAll(none, true);
      Gtk.windowSetTitlebar(newWindow, none);
      if (parameters.transparent()) {
        // The frame would outline the whole window around the shape that the page draws.
        // gtk_window_set_decorated(FALSE) drops it too, but KWin on Wayland then adds its own bar.
        Gtk.widgetAddCss(newWindow, FRAMELESS_CSS);
      }
    } else if (!parameters.minimizable() || !parameters.maximizable()) {
      MemorySegment bar = this.titleBar(parameters);
      Gtk.windowSetTitlebar(newWindow, bar);
      this.headerBar = bar;
    }
    if (!parameters.closable()) {
      Gtk.windowSetDeletable(newWindow, false);
    }
    if (parameters.transparent()) {
      Gtk.windowClearBackground(newWindow);
    }

    MemorySegment manager = WebKit.userContentManagerNew();
    this.listenToBridge(manager);
    if (!WebKit.registerScriptMessageHandler(manager, BridgeProtocol.CHANNEL)) {
      throw new IllegalStateException(
          "Message handler '" + BridgeProtocol.CHANNEL + "' already registered");
    }

    MemorySegment newWebView = WebKit.webViewNew(manager);
    if (parameters.transparent()) {
      WebKit.setTransparentBackground(newWebView);
    }
    this.configureWebView(newWebView);
    MemorySegment newBox = Gtk.boxNew(Gtk.ORIENTATION_VERTICAL, 0);
    Gtk.boxPackStart(newBox, newWebView, true);
    Gtk.containerAdd(newWindow, newBox);
    MemorySegment group = Gtk.accelGroupNew();
    Gtk.windowAddAccelGroup(newWindow, group);
    Glib.unref(group);

    MemorySegment userData = this.callbackData();
    Glib.signalConnect(newWindow, "delete-event", ON_DELETE_EVENT, userData);
    // Every change that a window event reports comes through one of these.
    for (String signal :
        List.of("configure-event", "window-state-event", "focus-in-event", "focus-out-event")) {
      Glib.signalConnect(newWindow, signal, ON_WINDOW_EVENT, userData);
    }
    Glib.signalConnect(newWebView, "context-menu", ON_CONTEXT_MENU, userData);
    Glib.signalConnect(newWebView, "drag-motion", ON_DRAG_MOTION, userData);
    Glib.signalConnect(newWebView, "drag-data-received", ON_DRAG_DATA_RECEIVED, userData);

    this.box = newBox;
    this.accelGroup = group;
    this.adopt(newWindow, newWebView, manager);
  }

  @Override
  protected MemorySegment downloadSource(MemorySegment webView) {
    return WebKit.downloadSource(webView);
  }

  @Override
  protected void downloadDestination(MemorySegment download, Path destination) {
    WebKit.downloadSetDestination(download, destination);
  }

  @Override
  protected void receiveBridgeMessage(MemorySegment message) {
    this.handleBridgeMessage(WebKit.scriptMessageText(message));
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

  /**
   * The icon of the title bar and the taskbar on X11; Wayland takes it from the {@code .desktop}.
   */
  @Override
  protected void presentIcon(byte[] png) {
    if (png == null) {
      Gtk.windowSetIcon(this.window(), MemorySegment.NULL);
      Gdk.clearWindowIcons(Gtk.widgetGetWindow(this.window()));
      return;
    }
    MemorySegment pixbuf = GdkPixbuf.decode(png);
    try {
      Gtk.windowSetIcon(this.window(), pixbuf);
    } finally {
      Glib.unref(pixbuf);
    }
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
  public WindowSize size() {
    return this.dispatcher()
        .call(
            () -> {
              int[] size = Gtk.windowGetSize(this.window());
              return new WindowSize(size[0], size[1]);
            });
  }

  /** Keeps the size within the limits itself, which GTK leaves to the window manager. */
  @Override
  public void size(int width, int height) {
    this.dispatcher()
        .run(
            () -> {
              int limitedWidth =
                  GtkWindow.limited(width, this.minimumSize.width(), this.maximumSize.width());
              int limitedHeight =
                  GtkWindow.limited(height, this.minimumSize.height(), this.maximumSize.height());
              Gtk.windowSetDefaultSize(this.window(), limitedWidth, limitedHeight);
              Gtk.windowResize(this.window(), limitedWidth, limitedHeight);
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
  public Screen screen() {
    return this.dispatcher().call(() -> GtkScreens.at(Gtk.widgetGetWindow(this.window())));
  }

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

  /**
   * Sets the limits, and brings the window within them. A window manager of X11 resizes a window
   * whose hints it no longer fits, but a compositor of Wayland only learns the limits for the next
   * resize of the user, so the window resizes itself.
   */
  private void applySizeLimits() {
    MemorySegment current = this.window();
    Gtk.windowSetSizeLimits(
        current,
        this.minimumSize.width(),
        this.minimumSize.height(),
        this.maximumSize.width(),
        this.maximumSize.height());
    int[] size = Gtk.windowGetSize(current);
    int width = GtkWindow.limited(size[0], this.minimumSize.width(), this.maximumSize.width());
    int height = GtkWindow.limited(size[1], this.minimumSize.height(), this.maximumSize.height());
    if (width != size[0] || height != size[1]) {
      Gtk.windowResize(current, width, height);
    }
  }

  /** {@code value} within {@code minimum} and {@code maximum}, where 0 is no limit. */
  private static int limited(int value, int minimum, int maximum) {
    int raised = Math.max(value, minimum);
    return maximum > 0 ? Math.min(raised, maximum) : raised;
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

  /**
   * On Wayland, asks again two frames later while the window is still maximized: a compositor such
   * as Weston drops an unmaximize that comes before the window drew its maximized frame, and sends
   * nothing back, so the window would stay maximized.
   */
  @Override
  public void restore() {
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window();
              Gtk.windowDeiconify(current);
              Gtk.windowUnmaximize(current);
              if (Gdk.isWayland() && Gtk.isWindowMaximized(current)) {
                if (this.unmaximizeFrames == 0) {
                  Gtk.widgetAddTickCallback(current, ON_TICK, this.callbackData());
                }
                this.unmaximizeFrames = 2;
              }
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
  protected void presentMenuBar(MenuCommands commands) {
    MemorySegment previous = this.menuBarWidget;
    if (previous != null) {
      this.menuBarWidget = null;
      Gtk.widgetDestroy(previous);
      this.menuBarBuilder.release();
      this.menuBarBuilder = null;
    }
    if (commands.isEmpty() || this.box == null) {
      return;
    }
    GtkMenuBuilder builder =
        new GtkMenuBuilder(commands, id -> this.menuItemPicked(commands, id), this.accelGroup);
    MemorySegment bar = builder.menuBar();
    Gtk.boxPackStart(this.box, bar, false);
    Gtk.boxReorderChild(this.box, bar, 0);
    this.menuBarWidget = bar;
    this.menuBarBuilder = builder;
  }

  /**
   * A {@code GtkMenu} attached to the web view, which goes away once it closes. {@code deactivate}
   * comes before the item picked activates, so the answer for no pick waits for the next turn of
   * the loop, and an item picked answers first.
   */
  @Override
  protected void presentContextMenu(
      MenuCommands commands, WindowPosition place, DialogCompletion<Integer> picked) {
    MemorySegment view = this.viewIfAny();
    if (view == null) {
      picked.complete(0);
      return;
    }
    GtkMenuBuilder builder = new GtkMenuBuilder(commands, picked::complete, MemorySegment.NULL);
    MemorySegment menu = builder.menu();
    Gtk.menuAttachToWidget(menu, view);
    builder.onDeactivate(
        menu,
        () ->
            this.dispatcher()
                .post(
                    () -> {
                      picked.complete(0);
                      builder.release();
                      Gtk.widgetDestroy(menu);
                    }));
    picked.onCancel(() -> Gtk.menuPopdown(menu));
    MemorySegment gdkWindow = Gtk.widgetGetWindow(view);
    int[] at = place != null ? new int[] {place.x(), place.y()} : Gdk.pointerPosition(gdkWindow);
    Gtk.menuPopupAt(menu, gdkWindow, at[0], at[1]);
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
  public void show() {
    this.dispatcher()
        .run(
            () -> {
              MemorySegment current = this.window();
              Gtk.widgetShowAll(current);
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
              MemorySegment current = this.windowIfAny();
              if (!this.isClosed() && current != null) {
                Gtk.widgetDestroy(current);
              }
            });
  }

  @Override
  protected void releaseWidgets() {
    this.headerBar = null;
    this.box = null;
    this.accelGroup = null;
    this.menuBarWidget = null;
    GtkMenuBuilder builder = this.menuBarBuilder;
    this.menuBarBuilder = null;
    if (builder != null) {
      builder.release();
    }
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
      GtkWindow window = WebKitGtkWindow.windowOf(userData, GtkWindow.class);
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
      GtkWindow window = WebKitGtkWindow.windowOf(userData, GtkWindow.class);
      if (window != null) {
        window.windowChanged();
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return 0;
  }

  /**
   * A frame of a window that waits to unmaximize, see {@link #restore()}.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   *
   * @return {@code TRUE} to hear the next frame, {@code FALSE} once done.
   */
  @SuppressWarnings({"unused", "resource"})
  private static int onTick(
      MemorySegment widget, MemorySegment frameClock, MemorySegment userData) {
    try {
      GtkWindow window = WebKitGtkWindow.windowOf(userData, GtkWindow.class);
      if (window == null) {
        return 0;
      }
      if (--window.unmaximizeFrames > 0) {
        return 1;
      }
      if (Gtk.isWindowMaximized(widget)) {
        Gtk.windowUnmaximize(widget);
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return 0;
  }

  /**
   * The pointer moves over the web view with a drag. This only remembers where, and doesn't answer
   * for the drag: WebKit decides whether the page accepts it.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static int onDragMotion(
      MemorySegment webView,
      MemorySegment context,
      int x,
      int y,
      int time,
      MemorySegment userData) {
    GtkWindow window = WebKitGtkWindow.windowOf(userData, GtkWindow.class);
    if (window != null) {
      window.dragX = x;
      window.dragY = y;
    }
    return 0;
  }

  /**
   * Something was dropped on the web view. WebKit acts on the drop itself, as the page decided;
   * this handler only reads the URIs of a file drop, and hands the paths to the window.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the window is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the window and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onDragDataReceived(
      MemorySegment webView,
      MemorySegment context,
      int x,
      int y,
      MemorySegment selectionData,
      int info,
      int time,
      MemorySegment userData) {
    try {
      GtkWindow window = WebKitGtkWindow.windowOf(userData, GtkWindow.class);
      if (window != null) {
        // WebKit asks for the data from its own handler of the drop, and the coordinates that come
        // with it are not the ones of the pointer; the last motion has them.
        window.filesDropped(
            FileDropUtil.pathsOfUris(Gtk.selectionDataUris(selectionData)),
            window.dragX,
            window.dragY);
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
      MemorySegment webView,
      MemorySegment menu,
      MemorySegment event,
      MemorySegment hitTest,
      MemorySegment userData) {
    try {
      GtkWindow window = WebKitGtkWindow.windowOf(userData, GtkWindow.class);
      if (window != null && WebKit.SHARED.isDeveloperExtrasEnabled(webView)) {
        return 0;
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return 1;
  }
}
