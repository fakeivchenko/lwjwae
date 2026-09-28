package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.shortcut.ShortcutModifier;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/** Presses keys for every application, with {@code keybd_event}, as a keyboard would. */
@UtilityClass
class Keyboard {
  private final int KEYEVENTF_KEYUP = 0x0002;

  private final MethodHandle KEYBD_EVENT =
      Linker.nativeLinker()
          .downcallHandle(
              SymbolLookup.libraryLookup("user32", Arena.global()).findOrThrow("keybd_event"),
              FunctionDescriptor.ofVoid(
                  ValueLayout.JAVA_BYTE,
                  ValueLayout.JAVA_BYTE,
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_LONG));

  /** Presses the modifiers of {@code shortcut}, its key, and lets them go in reverse. */
  @SneakyThrows
  void press(Shortcut shortcut) {
    List<Integer> keys = new ArrayList<>();
    if (shortcut.has(ShortcutModifier.CONTROL)) {
      keys.add(0x11);
    }
    if (shortcut.has(ShortcutModifier.ALT)) {
      keys.add(0x12);
    }
    if (shortcut.has(ShortcutModifier.SHIFT)) {
      keys.add(0x10);
    }
    if (shortcut.has(ShortcutModifier.META)) {
      keys.add(0x5B);
    }
    keys.add(WindowsShortcuts.virtualKey(shortcut.key()));
    for (int key : keys) {
      KEYBD_EVENT.invokeExact((byte) key, (byte) 0, 0, 0L);
    }
    for (int key : keys.reversed()) {
      KEYBD_EVENT.invokeExact((byte) key, (byte) 0, KEYEVENTF_KEYUP, 0L);
    }
  }
}
