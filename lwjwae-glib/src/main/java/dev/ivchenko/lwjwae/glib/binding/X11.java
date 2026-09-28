package dev.ivchenko.lwjwae.glib.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * The few calls of Xlib that a global shortcut needs on X11: the key grabs on the root window.
 *
 * <p>GTK runs on the same {@code Display} as these calls, which GDK hands out, so every call here
 * runs on the GTK thread. Loading this class loads {@code libX11}, which a GTK built for X11 has
 * loaded already.
 */
@UtilityClass
public class X11 {
  private final SymbolLookup XLIB = NativeLibraries.load("libX11.so.6", "libX11.so");

  /** {@code ShiftMask}, {@code LockMask}, {@code ControlMask}, {@code Mod1Mask} (Alt). */
  public final int SHIFT_MASK = 1;

  public final int LOCK_MASK = 1 << 1;
  public final int CONTROL_MASK = 1 << 2;
  public final int MOD1_MASK = 1 << 3;

  /** {@code Mod2Mask}: Num Lock on nearly every keyboard map. */
  public final int MOD2_MASK = 1 << 4;

  /** {@code Mod4Mask}: the logo key, Super. */
  public final int MOD4_MASK = 1 << 6;

  /** {@code KeyPress}, the type of an {@code XKeyEvent} of a pressed key. */
  public final int KEY_PRESS = 2;

  /** The offsets of {@code type}, {@code state}, and {@code keycode} in an {@code XKeyEvent}. */
  private final long EVENT_TYPE = 0;

  private final long EVENT_STATE = 80;
  private final long EVENT_KEYCODE = 84;

  private final int GRAB_MODE_ASYNC = 1;

  private final MethodHandle STRING_TO_KEYSYM =
      NativeLibraries.downcall(XLIB, "XStringToKeysym", Signatures.LONG_POINTER);
  private final MethodHandle KEYSYM_TO_KEYCODE =
      NativeLibraries.downcall(XLIB, "XKeysymToKeycode", Signatures.BYTE_POINTER_LONG);
  private final MethodHandle DEFAULT_ROOT_WINDOW =
      NativeLibraries.downcall(XLIB, "XDefaultRootWindow", Signatures.LONG_POINTER);
  private final MethodHandle GRAB_KEY =
      NativeLibraries.downcall(XLIB, "XGrabKey", Signatures.X_GRAB_KEY);
  private final MethodHandle UNGRAB_KEY =
      NativeLibraries.downcall(XLIB, "XUngrabKey", Signatures.INT_POINTER_INT_INT_LONG);

  /**
   * The key code that types the keysym named {@code name}, such as {@code a} or {@code Page_Up}, in
   * the keyboard map of {@code display}, or 0 when no key types it.
   */
  @SneakyThrows
  public int keycode(MemorySegment display, String name) {
    long keysym;
    try (Arena arena = Arena.ofConfined()) {
      keysym = (long) STRING_TO_KEYSYM.invokeExact(arena.allocateFrom(name));
    }
    if (keysym == 0) {
      return 0;
    }
    return Byte.toUnsignedInt((byte) KEYSYM_TO_KEYCODE.invokeExact(display, keysym));
  }

  /** {@code XDefaultRootWindow}. */
  @SneakyThrows
  public long rootWindow(MemorySegment display) {
    return (long) DEFAULT_ROOT_WINDOW.invokeExact(display);
  }

  /**
   * {@code XGrabKey} on {@code window}, the events going on as they would. A grab that another
   * client holds fails with {@code BadAccess}, which only an error trap around the call sees.
   */
  @SneakyThrows
  public void grabKey(MemorySegment display, int keycode, int modifiers, long window) {
    int _ =
        (int)
            GRAB_KEY.invokeExact(
                display, keycode, modifiers, window, 1, GRAB_MODE_ASYNC, GRAB_MODE_ASYNC);
  }

  /** {@code XUngrabKey}. */
  @SneakyThrows
  public void ungrabKey(MemorySegment display, int keycode, int modifiers, long window) {
    int _ = (int) UNGRAB_KEY.invokeExact(display, keycode, modifiers, window);
  }

  /** Whether {@code event}, an {@code XEvent}, is a pressed key. */
  public boolean isKeyPress(MemorySegment event) {
    return X11.field(event, EVENT_TYPE) == KEY_PRESS;
  }

  /** The key code of a key event. */
  public int eventKeycode(MemorySegment event) {
    return X11.field(event, EVENT_KEYCODE);
  }

  /** The modifiers that were down, of a key event. */
  public int eventState(MemorySegment event) {
    return X11.field(event, EVENT_STATE);
  }

  private int field(MemorySegment event, long offset) {
    return event.reinterpret(EVENT_KEYCODE + Integer.BYTES).get(Signatures.C_INT, offset);
  }
}
