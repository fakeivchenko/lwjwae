package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.glib.FakeStatusNotifierWatcher;
import dev.ivchenko.lwjwae.glib.StatusNotifierTray;
import dev.ivchenko.lwjwae.testing.DisplayAssumptions;
import dev.ivchenko.lwjwae.testing.Icons;
import dev.ivchenko.lwjwae.testing.Tags;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The StatusNotifierItem tray inside a GTK 3 process: the way the backend takes where
 * libappindicator is missing. The application picks libappindicator on this machine if it has it,
 * so the test builds the tray itself.
 */
@Tag(Tags.DISPLAY)
class GtkStatusNotifierTrayTest {
  @Test
  void trayServedOverDbusRunsItsHandlersInGtk3() throws Exception {
    Assumptions.assumeTrue(PlatformUtil.isUnixDesktop(), "Linux only");
    DisplayAssumptions.assumeDisplay();
    CountDownLatch picked = new CountDownLatch(1);
    try (Application application = Application.create()) {
      FakeStatusNotifierWatcher.ensureRunning(GtkDispatcher.instance());
      StatusNotifierTray tray =
          new StatusNotifierTray(
              GtkDispatcher.instance(),
              TrayIcon.builder()
                  .icon(Icons.circle(32, Color.GREEN))
                  .menu(List.of(new TrayMenuItem("Pick", picked::countDown)))
                  .build(),
              _ -> {});
      try (StatusNotifierTray _ = tray) {
        tray.simulateMenuClick(0);
        Assertions.assertTrue(picked.await(5, TimeUnit.SECONDS), "the entry must run");
      }
      Assertions.assertTrue(tray.isClosed());
      Assertions.assertFalse(application.isClosed());
    }
  }
}
