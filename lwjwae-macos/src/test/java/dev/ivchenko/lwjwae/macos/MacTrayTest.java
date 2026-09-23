package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.testing.Icons;
import dev.ivchenko.lwjwae.testing.contract.TrayContractTest;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.awt.Color;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MacTrayTest extends TrayContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }

  @Test
  void primaryClickRunsTheActivateHandler() throws Exception {
    try (Application application = Application.create()) {
      CountDownLatch activated = new CountDownLatch(1);
      MacTray tray =
          (MacTray)
              application.tray(
                  TrayIcon.builder()
                      .icon(Icons.circle(32, Color.GREEN))
                      .onActivate(activated::countDown)
                      .build());

      tray.simulateClick();
      Assertions.assertTrue(activated.await(5, TimeUnit.SECONDS), "onActivate must run");
    }
  }
}
