package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.testing.Icons;
import dev.ivchenko.lwjwae.testing.contract.TrayContractTest;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Gtk4TrayTest extends TrayContractTest {
  /** A session without a panel, such as the one on CI, gets a watcher that accepts the items. */
  @BeforeAll
  static void startWatcherWhereThereIsNone() {
    if (PlatformUtil.isUnixDesktop()
        && (System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null)) {
      FakeStatusNotifierWatcher.ensureRunning();
    }
  }

  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }

  @Test
  void menuEntriesAndThePrimaryClickRunTheirHandlers() throws Exception {
    CountDownLatch activated = new CountDownLatch(1);
    CountDownLatch picked = new CountDownLatch(1);
    try (Application application = Application.create();
        Tray tray =
            application.tray(
                TrayIcon.builder()
                    .icon(Icons.circle(32, Color.GREEN))
                    .menu(
                        List.of(
                            new TrayMenuItem("Disabled", () -> Assertions.fail("disabled"), false),
                            TrayMenuItem.separator(),
                            new TrayMenuItem("Pick", picked::countDown)))
                    .onActivate(activated::countDown)
                    .build())) {
      Gtk4Tray icon = (Gtk4Tray) tray;
      icon.simulateMenuClick(0);
      icon.simulateMenuClick(1);
      icon.simulateMenuClick(2);
      icon.simulateActivate();
      Assertions.assertTrue(picked.await(5, TimeUnit.SECONDS), "the enabled entry must run");
      Assertions.assertTrue(activated.await(5, TimeUnit.SECONDS), "onActivate must run");
    }
  }
}
