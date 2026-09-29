package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.glib.binding.X11;
import dev.ivchenko.lwjwae.glib.util.KeysymUtil;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.shortcut.ShortcutModifier;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Presses keys on X11 as a keyboard would, with the XTest extension, on a connection of its own:
 * the X server treats the press like one of the hardware, grabs included.
 */
@UtilityClass
public class XtestKeyboard {
  private final Linker LINKER = Linker.nativeLinker();
  private final SymbolLookup XLIB = SymbolLookup.libraryLookup("libX11.so.6", Arena.global());
  private final SymbolLookup XTST = SymbolLookup.libraryLookup("libXtst.so.6", Arena.global());

  private final MethodHandle OPEN_DISPLAY =
      XtestKeyboard.downcall(
          XLIB, "XOpenDisplay", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
  private final MethodHandle CLOSE_DISPLAY =
      XtestKeyboard.downcall(
          XLIB, "XCloseDisplay", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
  private final MethodHandle SYNC =
      XtestKeyboard.downcall(
          XLIB,
          "XSync",
          FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
  private final MethodHandle FAKE_KEY_EVENT =
      XtestKeyboard.downcall(
          XTST,
          "XTestFakeKeyEvent",
          FunctionDescriptor.of(
              ValueLayout.JAVA_INT,
              ValueLayout.ADDRESS,
              ValueLayout.JAVA_INT,
              ValueLayout.JAVA_INT,
              ValueLayout.JAVA_LONG));

  /** Presses the modifiers of {@code shortcut}, its key, and lets them go in reverse. */
  public void press(Shortcut shortcut) {
    List<String> keys = new ArrayList<>();
    if (shortcut.has(ShortcutModifier.CONTROL)) {
      keys.add("Control_L");
    }
    if (shortcut.has(ShortcutModifier.ALT)) {
      keys.add("Alt_L");
    }
    if (shortcut.has(ShortcutModifier.SHIFT)) {
      keys.add("Shift_L");
    }
    if (shortcut.has(ShortcutModifier.META)) {
      keys.add("Super_L");
    }
    keys.add(KeysymUtil.name(shortcut.key()));
    XtestKeyboard.chord(keys);
  }

  /**
   * Presses and lets go of each of {@code keysyms} in turn, such as {@code Down} and {@code
   * Return}.
   */
  public void tap(String... keysyms) {
    for (String keysym : keysyms) {
      XtestKeyboard.chord(List.of(keysym));
    }
  }

  /** Presses {@code keys} in order, and lets them go in reverse. */
  @SneakyThrows
  private void chord(List<String> keys) {
    MemorySegment display = (MemorySegment) OPEN_DISPLAY.invokeExact(MemorySegment.NULL);
    if (display.equals(MemorySegment.NULL)) {
      throw new IllegalStateException("No X display to press keys on");
    }
    try {
      for (String key : keys) {
        int _ = (int) FAKE_KEY_EVENT.invokeExact(display, X11.keycode(display, key), 1, 0L);
      }
      for (String key : keys.reversed()) {
        int _ = (int) FAKE_KEY_EVENT.invokeExact(display, X11.keycode(display, key), 0, 0L);
      }
      int _ = (int) SYNC.invokeExact(display, 0);
    } finally {
      int _ = (int) CLOSE_DISPLAY.invokeExact(display);
    }
  }

  private MethodHandle downcall(SymbolLookup library, String name, FunctionDescriptor descriptor) {
    return LINKER.downcallHandle(library.findOrThrow(name), descriptor);
  }
}
