package dev.ivchenko.lwjwae.shortcut;

import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ShortcutTest {
  @Test
  void parseReadsTheModifiersAndTheKeyInAnyCase() {
    Assertions.assertEquals(
        Shortcut.of(ShortcutKey.K, ShortcutModifier.CONTROL, ShortcutModifier.SHIFT),
        Shortcut.parse("ctrl + Shift+k"));
    Assertions.assertEquals(
        Shortcut.of(ShortcutKey.PAGE_UP, ShortcutModifier.META, ShortcutModifier.ALT),
        Shortcut.parse("Super+Option+PageUp"));
    Assertions.assertEquals(
        Shortcut.of(ShortcutKey.ESCAPE, ShortcutModifier.ALT), Shortcut.parse("Alt+Esc"));
    Assertions.assertEquals(
        Shortcut.of(ShortcutKey.DIGIT_7, ShortcutModifier.ALT), Shortcut.parse("Alt+7"));
    Assertions.assertEquals(Shortcut.of(ShortcutKey.F13), Shortcut.parse("F13"));
  }

  @Test
  void cmdOrCtrlIsCommandOnMacOsAndControlElsewhere() {
    ShortcutModifier expected =
        PlatformUtil.isMacOs() ? ShortcutModifier.META : ShortcutModifier.CONTROL;
    Assertions.assertEquals(Set.of(expected), Shortcut.parse("CmdOrCtrl+Space").modifiers());
    Assertions.assertEquals(Set.of(expected), Shortcut.parse("CommandOrControl+Space").modifiers());
  }

  @Test
  void textComesBackWithTheModifiersInOrder() {
    Assertions.assertEquals(
        "Ctrl+Alt+Shift+Meta+Enter", Shortcut.parse("Meta+Shift+Alt+Ctrl+Return").toString());
    Assertions.assertEquals("F5", Shortcut.parse("f5").toString());
    Shortcut shortcut = Shortcut.parse("Ctrl+Shift+PageDown");
    Assertions.assertEquals(shortcut, Shortcut.parse(shortcut.toString()));
  }

  @Test
  void keyWithoutModifierIsRefusedUnlessItIsFunctionKey() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> Shortcut.parse("K"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Shortcut.parse("Space"));
    Assertions.assertDoesNotThrow(() -> Shortcut.parse("F24"));
  }

  @Test
  void unknownPartsAreRefused() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> Shortcut.parse("Hyper+K"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Shortcut.parse("Ctrl+F25"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Shortcut.parse("Ctrl+"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Shortcut.parse("Ctrl+;"));
  }

  @Test
  void maskOrsTheBitsOfTheModifiersHeld() {
    Assertions.assertEquals(1 | 4, Shortcut.parse("Ctrl+Shift+K").mask(1, 2, 4, 8));
    Assertions.assertEquals(8, Shortcut.parse("Meta+Space").mask(1, 2, 4, 8));
    Assertions.assertEquals(0, Shortcut.parse("F1").mask(1, 2, 4, 8));
  }
}
