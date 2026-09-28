package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.shortcut.Shortcut;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PortalShortcutsTest {
  @Test
  void triggerNamesTheModifiersAndTheKeysymAsTheSpecificationDoes() {
    Assertions.assertEquals(
        "CTRL+SHIFT+Page_Up", PortalShortcuts.trigger(Shortcut.parse("Shift+Ctrl+PageUp")));
    Assertions.assertEquals("ALT+LOGO+k", PortalShortcuts.trigger(Shortcut.parse("Meta+Alt+K")));
    Assertions.assertEquals("F13", PortalShortcuts.trigger(Shortcut.parse("F13")));
  }
}
