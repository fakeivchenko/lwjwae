package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.testing.Tags;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The {@code color-scheme} of the desktop portal reaches a Linux backend, from the first read to
 * the signal of a change. It needs the portal and a settings backend on the session bus, which the
 * Linux image and the Linux job of CI have; {@code gsettings} plays the user at the settings of the
 * desktop, as GNOME does.
 */
@Tag(Tags.DISPLAY)
@Timeout(60)
public abstract class PortalThemeContractTest {
  private static final String SETTING = "org.gnome.desktop.interface";
  private static final String KEY = "color-scheme";

  /** Whether the machine of this test is of the platform of the backend. */
  protected abstract boolean isThisPlatform();

  @Test
  void theColorSchemeOfThePortalIsReadAndFollowed() throws Exception {
    Assumptions.assumeTrue(this.isThisPlatform(), "Linux only");
    // The portal offers the settings of a backend that is on the bus when it starts, and the bus
    // starts the backend only afterwards, so the test starts the backend first.
    Process backend = PortalThemeContractTest.startBackend();
    boolean ready = backend != null && PortalThemeContractTest.portalAnswers();
    if (Boolean.getBoolean("lwjwae.requireDisplay")) {
      Assertions.assertTrue(ready, "the image has a portal that serves the color scheme");
    } else {
      Assumptions.assumeTrue(ready, "no portal that serves the color scheme");
    }
    try {
      PortalThemeContractTest.choose("prefer-dark");
      try (Application application = Application.create()) {
        Assertions.assertEquals(
            SystemTheme.DARK, application.theme(), "the first read finds the choice of the user");

        BlockingQueue<SystemTheme> heard = new LinkedBlockingQueue<>();
        application.onThemeChange(heard::add);
        PortalThemeContractTest.choose("prefer-light");
        Assertions.assertEquals(SystemTheme.LIGHT, heard.poll(10, TimeUnit.SECONDS));
        PortalThemeContractTest.choose("prefer-dark");
        Assertions.assertEquals(SystemTheme.DARK, heard.poll(10, TimeUnit.SECONDS));
        Assertions.assertEquals(SystemTheme.DARK, application.theme());
      }
    } finally {
      PortalThemeContractTest.choose("default");
      if (backend != null) {
        backend.destroy();
      }
    }
  }

  /** The backend of the portal for GTK, or {@code null} where it isn't installed. */
  private static Process startBackend() throws Exception {
    Path executable = Path.of("/usr/libexec/xdg-desktop-portal-gtk");
    if (!Files.isExecutable(executable)) {
      return null;
    }
    Process backend =
        new ProcessBuilder(executable.toString())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    for (int attempt = 0; attempt < 40; attempt++) {
      if (PortalThemeContractTest.run(
          List.of(
              "gdbus",
              "call",
              "--session",
              "--dest",
              "org.freedesktop.DBus",
              "--object-path",
              "/org/freedesktop/DBus",
              "--method",
              "org.freedesktop.DBus.GetNameOwner",
              "org.freedesktop.impl.portal.desktop.gtk"))) {
        break;
      }
      Thread.sleep(250);
    }
    return backend;
  }

  /**
   * Whether the portal answers {@code Read} of the color scheme, once the setting exists. The bus
   * starts the portal and its backend on the first call, which may fail while they come up.
   */
  private static boolean portalAnswers() throws Exception {
    if (!PortalThemeContractTest.run(List.of("gsettings", "set", SETTING, KEY, "default"))) {
      return false;
    }
    for (int attempt = 0; attempt < 20; attempt++) {
      if (PortalThemeContractTest.run(
          List.of(
              "gdbus",
              "call",
              "--session",
              "--dest",
              "org.freedesktop.portal.Desktop",
              "--object-path",
              "/org/freedesktop/portal/desktop",
              "--method",
              "org.freedesktop.portal.Settings.Read",
              "org.freedesktop.appearance",
              "color-scheme"))) {
        return true;
      }
      Thread.sleep(500);
    }
    return false;
  }

  private static void choose(String scheme) throws Exception {
    Assertions.assertTrue(
        PortalThemeContractTest.run(List.of("gsettings", "set", SETTING, KEY, scheme)),
        "gsettings sets " + scheme);
  }

  private static boolean run(List<String> command) throws Exception {
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      process.getInputStream().readAllBytes();
      return process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0;
    } catch (java.io.IOException _) {
      // No such tool on this machine.
      return false;
    }
  }
}
