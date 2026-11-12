package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class StatusNotifierTrayTest {
  @Test
  void labelDoublesEveryUnderscoreSoNoneIsAMnemonic() {
    Assumptions.assumeTrue(PlatformUtil.isUnixDesktop(), "Binds GLib: Linux only");
    Assertions.assertEquals(
        "Save__as__copy", StatusNotifierTray.label(new TrayMenuItem("Save_as_copy", () -> {})));
    Assertions.assertEquals("Quit", StatusNotifierTray.label(new TrayMenuItem("Quit", () -> {})));
  }
}
