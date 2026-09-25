package dev.ivchenko.lwjwae.macos.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import lombok.experimental.UtilityClass;

/** AppKit: the application object, windows, the status bar, and menus. */
@UtilityClass
public class AppKit {
  static {
    // Loaded for the classes it registers; nothing is looked up by symbol.
    SymbolLookup _ = NativeLibraries.load("/System/Library/Frameworks/AppKit.framework/AppKit");
  }

  /** {@code NSWindowStyleMaskResizable}. */
  public final long STYLE_RESIZABLE = 1 << 3;

  /** {@code NSWindowStyleMaskTitled}. */
  public final long STYLE_TITLED = 1;

  /** {@code NSWindowStyleMaskClosable}. */
  public final long STYLE_CLOSABLE = 1 << 1;

  /** {@code NSWindowStyleMaskMiniaturizable}. */
  public final long STYLE_MINIATURIZABLE = 1 << 2;

  /** {@code NSWindowStyleMaskFullSizeContentView}: the content reaches under the title bar. */
  public final long STYLE_FULL_SIZE_CONTENT_VIEW = 1 << 15;

  private final long WINDOW_TITLE_HIDDEN = 1;
  private final long ZOOM_BUTTON = 2;
  private final long EVENT_TYPE_LEFT_MOUSE_DOWN = 1;
  private final long EVENT_TYPE_LEFT_MOUSE_DRAGGED = 6;
  private final long ACTIVATION_POLICY_REGULAR = 0;
  private final long BACKING_STORE_BUFFERED = 2;
  private final long EVENT_TYPE_APPLICATION_DEFINED = 15;
  private final long EVENT_TYPE_RIGHT_MOUSE_DOWN = 3;
  private final long EVENT_TYPE_RIGHT_MOUSE_UP = 4;
  private final long EVENT_MODIFIER_CONTROL = 1 << 18;
  private final long EVENT_MASK_LEFT_AND_RIGHT_MOUSE_UP = (1 << 2) | (1 << 4);
  private final double VARIABLE_STATUS_ITEM_LENGTH = -1;
  private final double STATUS_ICON_SIZE = 18;
  private final String OTHER_EVENT_SELECTOR =
      "otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:"
          + "data1:data2:";

  /**
   * {@code +[NSApplication sharedApplication]}, configured as a regular application with a Dock
   * icon and a menu bar.
   */
  public MemorySegment application() {
    MemorySegment application = ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
    boolean _ = ObjC.sendBool(application, "setActivationPolicy:", ACTIVATION_POLICY_REGULAR);
    return application;
  }

  /**
   * {@code -[NSApplication finishLaunching]}: what {@code run} does first. It's called ahead of
   * {@code run} when windows are created before the loop starts, because WebKit starts its helper
   * processes only in an application that has launched.
   */
  public void finishLaunching() {
    ObjC.sendVoid(application(), "finishLaunching");
  }

  /** {@code -[NSApplication run]}. It returns only after {@link #stopRunLoop}. */
  public void run() {
    ObjC.sendVoid(application(), "run");
  }

  /**
   * {@code -[NSApplication stop:]} followed by an application-defined event. {@code stop:} takes
   * effect only after the run loop processes an event, so an event is posted to make sure that it
   * does.
   */
  public void stopRunLoop() {
    MemorySegment application = application();
    ObjC.sendVoid(application, "stop:", MemorySegment.NULL);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment event =
          ObjC.sendOtherEvent(
              ObjC.cls("NSEvent"),
              OTHER_EVENT_SELECTOR,
              EVENT_TYPE_APPLICATION_DEFINED,
              Foundation.point(arena, 0, 0),
              0L,
              0.0,
              0L,
              MemorySegment.NULL,
              (short) 0,
              0L,
              0L);
      ObjC.sendVoid(application, "postEvent:atStart:", event, true);
    }
  }

  /** Brings the application to the front, the way a newly launched application comes up. */
  public void activate() {
    ObjC.sendVoid(application(), "activateIgnoringOtherApps:", true);
  }

  /**
   * A window of {@code styleMask} that owns its content rect, centered. The caller retains it, not
   * its closing.
   */
  public MemorySegment window(int width, int height, String title, long styleMask) {
    MemorySegment window;
    try (Arena arena = Arena.ofConfined()) {
      window =
          ObjC.sendWithRect(
              ObjC.send(ObjC.cls("NSWindow"), "alloc"),
              "initWithContentRect:styleMask:backing:defer:",
              Foundation.rect(arena, 0, 0, width, height),
              styleMask,
              BACKING_STORE_BUFFERED,
              false);
    }
    ObjC.sendVoid(window, "setReleasedWhenClosed:", false);
    setTitle(window, title);
    ObjC.sendVoid(window, "center");
    return window;
  }

  /** Calls {@code -[NSWindow setTitle:]}. */
  public void setTitle(MemorySegment window, String title) {
    ObjC.sendVoid(window, "setTitle:", Foundation.string(title));
  }

  /** Calls {@code -[NSWindow title]}. */
  public String title(MemorySegment window) {
    return Foundation.string(ObjC.send(window, "title"));
  }

  /** {@code {width, height}} of the content area. */
  public int[] contentSize(MemorySegment window) {
    double[] frame = Foundation.rect(ObjC.send(window, "contentView"), "frame");
    return new int[] {(int) Math.round(frame[2]), (int) Math.round(frame[3])};
  }

  /** Calls {@code -[NSWindow setContentSize:]}: resizes the content area, and the frame with it. */
  public void setContentSize(MemorySegment window, int width, int height) {
    try (Arena arena = Arena.ofConfined()) {
      ObjC.sendVoidSize(window, "setContentSize:", Foundation.size(arena, width, height));
    }
  }

  /**
   * {@code {x, y}} of the top left of the window frame, measured from the top left of the primary
   * screen. AppKit measures from the bottom left, so the height of the primary screen turns one
   * into the other.
   */
  public int[] framePosition(MemorySegment window) {
    double[] frame = Foundation.rect(window, "frame");
    double screenHeight = primaryScreenFrame()[3];
    return new int[] {
      (int) Math.round(frame[0]), (int) Math.round(screenHeight - frame[1] - frame[3])
    };
  }

  /**
   * Calls {@code -[NSWindow setFrameOrigin:]} with the top left of the frame given from the top
   * left of the primary screen, converted as {@link #framePosition} does.
   */
  public void setFramePosition(MemorySegment window, int x, int y) {
    double[] frame = Foundation.rect(window, "frame");
    double screenHeight = primaryScreenFrame()[3];
    try (Arena arena = Arena.ofConfined()) {
      // An NSPoint has the layout of an NSSize: two doubles.
      ObjC.sendVoidSize(
          window, "setFrameOrigin:", Foundation.size(arena, x, screenHeight - y - frame[3]));
    }
  }

  /** Calls {@code -[NSWindow center]}. */
  public void center(MemorySegment window) {
    ObjC.sendVoid(window, "center");
  }

  /**
   * {@code {x, y, width, height}} of the primary screen, the one with the menu bar, whose bottom
   * left is the origin of the screen coordinates of AppKit.
   */
  private double[] primaryScreenFrame() {
    MemorySegment screens = ObjC.send(ObjC.cls("NSScreen"), "screens");
    return Foundation.rect(ObjC.send(screens, "firstObject"), "frame");
  }

  /** {@code NSWindowStyleMaskFullScreen}: the window is in a full screen space of its own. */
  public final long STYLE_FULL_SCREEN = 1 << 14;

  /** {@code NSNormalWindowLevel}. */
  private final long LEVEL_NORMAL = 0;

  /** {@code NSFloatingWindowLevel}: above the normal windows of every application. */
  private final long LEVEL_FLOATING = 3;

  /** The content size that stands for no maximum: {@code FLT_MAX}, AppKit's own default. */
  private final double UNLIMITED = Float.MAX_VALUE;

  /**
   * Calls {@code -[NSWindow miniaturize:]} or {@code -[NSWindow deminiaturize:]}, only when the
   * state differs, as the other state changes are sent.
   */
  public void setMiniaturized(MemorySegment window, boolean miniaturized) {
    if (isMiniaturized(window) == miniaturized) {
      return;
    }
    ObjC.sendVoid(window, miniaturized ? "miniaturize:" : "deminiaturize:", MemorySegment.NULL);
  }

  /** Calls {@code -[NSWindow isMiniaturized]}. */
  public boolean isMiniaturized(MemorySegment window) {
    return ObjC.sendBool(window, "isMiniaturized");
  }

  /**
   * Zooms the window, the way the green button does with the Option key: to fill the screen, or
   * back. {@code -[NSWindow zoom:]} toggles, so it's sent only when the state differs, and it acts
   * as the button does, so a button that {@link #disableZoomButton} grayed out is enabled for it.
   */
  public void setZoomed(MemorySegment window, boolean zoomed) {
    if (isZoomed(window) == zoomed) {
      return;
    }
    MemorySegment button = ObjC.send(window, "standardWindowButton:", ZOOM_BUTTON);
    boolean enabled = ObjC.sendBool(button, "isEnabled");
    ObjC.sendVoid(button, "setEnabled:", true);
    ObjC.sendVoid(window, "zoom:", MemorySegment.NULL);
    ObjC.sendVoid(button, "setEnabled:", enabled);
  }

  /** Calls {@code -[NSWindow isZoomed]}. */
  public boolean isZoomed(MemorySegment window) {
    return ObjC.sendBool(window, "isZoomed");
  }

  /**
   * Enters or leaves full screen with {@code -[NSWindow toggleFullScreen:]}, which toggles, so it's
   * sent only when the state differs.
   */
  public void setFullScreen(MemorySegment window, boolean fullScreen) {
    if (((styleMask(window) & STYLE_FULL_SCREEN) != 0) != fullScreen) {
      ObjC.sendVoid(window, "toggleFullScreen:", MemorySegment.NULL);
    }
  }

  /** Puts the window on the floating level, above normal windows, or back on the normal one. */
  public void setFloating(MemorySegment window, boolean floating) {
    ObjC.sendVoid(window, "setLevel:", floating ? LEVEL_FLOATING : LEVEL_NORMAL);
  }

  /** Whether the window is on a level above the normal one. */
  public boolean isFloating(MemorySegment window) {
    return ObjC.sendLong(window, "level") > LEVEL_NORMAL;
  }

  /** Calls {@code -[NSWindow isKeyWindow]}: whether the window takes the keyboard input. */
  public boolean isKeyWindow(MemorySegment window) {
    return ObjC.sendBool(window, "isKeyWindow");
  }

  /**
   * Calls {@code -[NSWindow setContentMinSize:]} and {@code setContentMaxSize:}. Zero in a
   * dimension means no limit there.
   */
  public void setContentSizeLimits(
      MemorySegment window, int minWidth, int minHeight, int maxWidth, int maxHeight) {
    try (Arena arena = Arena.ofConfined()) {
      ObjC.sendVoidSize(window, "setContentMinSize:", Foundation.size(arena, minWidth, minHeight));
      ObjC.sendVoidSize(
          window,
          "setContentMaxSize:",
          Foundation.size(
              arena, maxWidth > 0 ? maxWidth : UNLIMITED, maxHeight > 0 ? maxHeight : UNLIMITED));
    }
  }

  /** Calls {@code -[NSWindow styleMask]}. */
  public long styleMask(MemorySegment window) {
    return ObjC.sendLong(window, "styleMask");
  }

  /** Calls {@code -[NSWindow setStyleMask:]}. */
  public void setStyleMask(MemorySegment window, long styleMask) {
    ObjC.sendVoid(window, "setStyleMask:", styleMask);
  }

  /** Calls {@code -[NSWindow setDelegate:]}. {@code NULL} detaches the delegate. */
  public void setDelegate(MemorySegment window, MemorySegment delegate) {
    ObjC.sendVoid(window, "setDelegate:", delegate);
  }

  /** Calls {@code -[NSWindow setContentView:]}: the window retains the view. */
  public void setContentView(MemorySegment window, MemorySegment view) {
    ObjC.sendVoid(window, "setContentView:", view);
  }

  /**
   * Takes the title bar of a window with {@link #STYLE_FULL_SIZE_CONTENT_VIEW} out of sight: no
   * background, no title, and no buttons. The window stays titled, which keeps its rounded corners,
   * its shadow, its resize edges, and the keyboard, which a borderless window can't take.
   */
  public void hideTitleBar(MemorySegment window) {
    ObjC.sendVoid(window, "setTitlebarAppearsTransparent:", true);
    ObjC.sendVoid(window, "setTitleVisibility:", WINDOW_TITLE_HIDDEN);
    for (long button = 0; button <= ZOOM_BUTTON; button++) {
      ObjC.sendVoid(ObjC.send(window, "standardWindowButton:", button), "setHidden:", true);
    }
  }

  /** Grays out the zoom button, the green one, which maximizes and enters full screen. */
  public void disableZoomButton(MemorySegment window) {
    ObjC.sendVoid(ObjC.send(window, "standardWindowButton:", ZOOM_BUTTON), "setEnabled:", false);
  }

  /**
   * Moves the window with the pointer until the button is released, the way a drag on the title bar
   * does: {@code -[NSWindow performWindowDragWithEvent:]} with the mouse event being handled. Does
   * nothing when that isn't a press or a drag of the left button, or the button is already up.
   */
  public void performWindowDrag(MemorySegment window) {
    if ((ObjC.sendLong(ObjC.cls("NSEvent"), "pressedMouseButtons") & 1) == 0) {
      return;
    }
    MemorySegment event = ObjC.send(application(), "currentEvent");
    if (ObjC.isNull(event)) {
      return;
    }
    long type = ObjC.sendLong(event, "type");
    if (type == EVENT_TYPE_LEFT_MOUSE_DOWN || type == EVENT_TYPE_LEFT_MOUSE_DRAGGED) {
      ObjC.sendVoid(window, "performWindowDragWithEvent:", event);
    }
  }

  /**
   * What the user chose in the Desktop and Dock settings for a double click on a title bar: {@code
   * Maximize}, {@code Minimize}, {@code None}, or {@code null} for the default, which zooms.
   */
  public String titleBarDoubleClickAction() {
    MemorySegment defaults = ObjC.send(ObjC.cls("NSUserDefaults"), "standardUserDefaults");
    return Foundation.string(
        ObjC.send(defaults, "stringForKey:", Foundation.string("AppleActionOnDoubleClick")));
  }

  /**
   * Opens {@code url} in the application that the system chose for its scheme, through {@code
   * -[NSWorkspace openURL:]}.
   *
   * @throws IllegalStateException If nothing opens it.
   */
  public void openUrl(String url) {
    MemorySegment workspace = ObjC.send(ObjC.cls("NSWorkspace"), "sharedWorkspace");
    MemorySegment nsUrl = Foundation.url(url);
    if (ObjC.isNull(nsUrl) || !ObjC.sendBool(workspace, "openURL:", nsUrl)) {
      throw new IllegalStateException("NSWorkspace could not open " + url);
    }
  }

  /** Calls {@code -[NSWindow makeKeyAndOrderFront:]}: shows the window and gives it focus. */
  public void show(MemorySegment window) {
    ObjC.sendVoid(window, "makeKeyAndOrderFront:", MemorySegment.NULL);
  }

  /**
   * Calls {@code -[NSWindow performClose:]}: what the close button does, including the {@code
   * windowShouldClose:} question to the delegate.
   */
  public void performClose(MemorySegment window) {
    ObjC.sendVoid(window, "performClose:", MemorySegment.NULL);
  }

  /** Calls {@code -[NSWindow orderOut:]}: takes the window off the screen and keeps it. */
  public void hide(MemorySegment window) {
    ObjC.sendVoid(window, "orderOut:", MemorySegment.NULL);
  }

  /** Calls {@code -[NSWindow isVisible]}. */
  public boolean isVisible(MemorySegment window) {
    return ObjC.sendBool(window, "isVisible");
  }

  /** {@code -[NSWindow close]}. The delegate receives {@code windowWillClose:} synchronously. */
  public void close(MemorySegment window) {
    ObjC.sendVoid(window, "close");
  }

  // --- the status bar ---

  /**
   * {@code -[NSStatusBar statusItemWithLength:]} on the system status bar, retained: the caller
   * gives it back with {@link #removeStatusItem}.
   */
  public MemorySegment statusItem() {
    MemorySegment bar = ObjC.send(ObjC.cls("NSStatusBar"), "systemStatusBar");
    return Foundation.retain(ObjC.send(bar, "statusItemWithLength:", VARIABLE_STATUS_ITEM_LENGTH));
  }

  /** Takes {@code item} off the system status bar and releases it. */
  public void removeStatusItem(MemorySegment item) {
    MemorySegment bar = ObjC.send(ObjC.cls("NSStatusBar"), "systemStatusBar");
    ObjC.sendVoid(bar, "removeStatusItem:", item);
    Foundation.release(item);
  }

  /** {@code -[NSStatusItem button]}: the view in the menu bar that shows the image. */
  public MemorySegment statusItemButton(MemorySegment item) {
    return ObjC.send(item, "button");
  }

  /**
   * Makes {@code button} send {@code action} to {@code target} on a release of either mouse button.
   * By default a status bar button answers the left one only.
   */
  public void setButtonAction(MemorySegment button, MemorySegment target, String action) {
    ObjC.sendVoid(button, "setTarget:", target);
    ObjC.sendVoid(button, "setAction:", ObjC.sel(action));
    // Answers the previous mask, which nothing needs: an integer result left in its register.
    ObjC.sendVoid(button, "sendActionOn:", EVENT_MASK_LEFT_AND_RIGHT_MOUSE_UP);
  }

  /**
   * Sets the image of {@code button} from PNG bytes, scaled to the height of the menu bar.
   *
   * @throws IllegalArgumentException If AppKit can't read the image.
   */
  public void setButtonImage(MemorySegment button, byte[] png) {
    MemorySegment image =
        ObjC.send(ObjC.send(ObjC.cls("NSImage"), "alloc"), "initWithData:", Foundation.data(png));
    if (ObjC.isNull(image)) {
      throw new IllegalArgumentException("The tray image is not an image AppKit can read");
    }
    try (Arena arena = Arena.ofConfined()) {
      ObjC.sendVoidSize(
          image, "setSize:", Foundation.size(arena, STATUS_ICON_SIZE, STATUS_ICON_SIZE));
    }
    ObjC.sendVoid(button, "setImage:", image);
    Foundation.release(image);
  }

  /** {@code -[NSView setToolTip:]}; {@code null} removes the tooltip. */
  public void setToolTip(MemorySegment view, String tooltip) {
    ObjC.sendVoid(
        view, "setToolTip:", tooltip == null ? MemorySegment.NULL : Foundation.string(tooltip));
  }

  /** {@code -[NSStatusItem setMenu:]}; {@code nil} lets the button send its action instead. */
  public void setStatusItemMenu(MemorySegment item, MemorySegment menu) {
    ObjC.sendVoid(item, "setMenu:", menu);
  }

  /** {@code -[NSButton performClick:]}. With a menu on the status item, opens it and tracks it. */
  public void performClick(MemorySegment button) {
    ObjC.sendVoid(button, "performClick:", MemorySegment.NULL);
  }

  /**
   * Checks whether the event being handled asks for a context menu: a click of the secondary
   * button, or a click with Control held, as everywhere on macOS.
   */
  public boolean isContextClick() {
    MemorySegment event = ObjC.send(application(), "currentEvent");
    if (ObjC.isNull(event)) {
      return false;
    }
    long type = ObjC.sendLong(event, "type");
    return type == EVENT_TYPE_RIGHT_MOUSE_DOWN
        || type == EVENT_TYPE_RIGHT_MOUSE_UP
        || (ObjC.sendLong(event, "modifierFlags") & EVENT_MODIFIER_CONTROL) != 0;
  }

  // --- menus ---

  /** A new, owned {@code NSMenu} that leaves the enabled state of its items alone. */
  public MemorySegment menu() {
    MemorySegment menu =
        ObjC.send(ObjC.send(ObjC.cls("NSMenu"), "alloc"), "initWithTitle:", Foundation.string(""));
    ObjC.sendVoid(menu, "setAutoenablesItems:", false);
    return menu;
  }

  /**
   * Adds an item that sends {@code action} to {@code target}, with {@code tag} to tell it apart.
   */
  public void addMenuItem(
      MemorySegment menu,
      String title,
      MemorySegment target,
      String action,
      long tag,
      boolean enabled) {
    MemorySegment item = ObjC.send(ObjC.send(ObjC.cls("NSMenuItem"), "alloc"), "init");
    ObjC.sendVoid(item, "setTitle:", Foundation.string(title));
    ObjC.sendVoid(item, "setTarget:", target);
    ObjC.sendVoid(item, "setAction:", ObjC.sel(action));
    ObjC.sendVoid(item, "setTag:", tag);
    ObjC.sendVoid(item, "setEnabled:", enabled);
    ObjC.sendVoid(menu, "addItem:", item);
    Foundation.release(item);
  }

  /** Adds a separator line. */
  public void addMenuSeparator(MemorySegment menu) {
    ObjC.sendVoid(menu, "addItem:", ObjC.send(ObjC.cls("NSMenuItem"), "separatorItem"));
  }

  /** {@code -[NSMenuItem tag]}. */
  public long menuItemTag(MemorySegment item) {
    return ObjC.sendLong(item, "tag");
  }
}
