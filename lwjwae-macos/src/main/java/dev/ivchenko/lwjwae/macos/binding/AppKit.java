package dev.ivchenko.lwjwae.macos.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import lombok.experimental.UtilityClass;

/** AppKit: the application object and windows. */
@UtilityClass
public class AppKit {
  static {
    // Loaded for the classes it registers; nothing is looked up by symbol.
    SymbolLookup _ = NativeLibraries.load("/System/Library/Frameworks/AppKit.framework/AppKit");
  }

  /** {@code NSWindowStyleMaskResizable}. */
  public final long STYLE_RESIZABLE = 1 << 3;

  private final long STYLE_TITLED_CLOSABLE_MINIATURIZABLE = 1 | (1 << 1) | (1 << 2);
  private final long ACTIVATION_POLICY_REGULAR = 0;
  private final long BACKING_STORE_BUFFERED = 2;
  private final long EVENT_TYPE_APPLICATION_DEFINED = 15;
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
   * A titled, closable, resizable window that owns its content rect. The caller retains it, not its
   * closing.
   */
  public MemorySegment window(int width, int height, String title) {
    MemorySegment window;
    try (Arena arena = Arena.ofConfined()) {
      window =
          ObjC.sendWithRect(
              ObjC.send(ObjC.cls("NSWindow"), "alloc"),
              "initWithContentRect:styleMask:backing:defer:",
              Foundation.rect(arena, 0, 0, width, height),
              STYLE_TITLED_CLOSABLE_MINIATURIZABLE | STYLE_RESIZABLE,
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

  /** Calls {@code -[NSWindow makeKeyAndOrderFront:]}: shows the window and gives it focus. */
  public void show(MemorySegment window) {
    ObjC.sendVoid(window, "makeKeyAndOrderFront:", MemorySegment.NULL);
  }

  /** {@code -[NSWindow close]}. The delegate receives {@code windowWillClose:} synchronously. */
  public void close(MemorySegment window) {
    ObjC.sendVoid(window, "close");
  }
}
