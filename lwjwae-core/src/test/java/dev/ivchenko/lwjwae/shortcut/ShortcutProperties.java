package dev.ivchenko.lwjwae.shortcut;

import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Assertions;

/** A shortcut reads back from its text, so that settings that store one keep it. */
class ShortcutProperties {
  @Property
  void everyShortcutReadsBackFromItsText(
      @ForAll ShortcutKey key, @ForAll("modifiers") Set<ShortcutModifier> modifiers) {
    Shortcut shortcut = new Shortcut(modifiers, key);

    Assertions.assertEquals(shortcut, Shortcut.parse(shortcut.toString()));
  }

  @Provide
  Arbitrary<Set<ShortcutModifier>> modifiers() {
    return Arbitraries.of(ShortcutModifier.values()).set().ofMinSize(1);
  }

  /** Any text is a shortcut or an {@link IllegalArgumentException}, never another failure. */
  @Property
  void anyTextIsReadOrRefused(@ForAll String text) {
    try {
      Shortcut.parse(text);
    } catch (IllegalArgumentException _) {
      // Not a shortcut: the one way to fail.
    }
  }
}
