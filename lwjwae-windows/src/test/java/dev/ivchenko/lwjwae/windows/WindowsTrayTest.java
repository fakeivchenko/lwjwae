package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.testing.Icons;
import dev.ivchenko.lwjwae.testing.contract.TrayContractTest;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.awt.Color;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WindowsTrayTest extends TrayContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }

  @Test
  void primaryClickRunsTheActivateHandler() throws Exception {
    try (Application application = Application.create()) {
      CountDownLatch activated = new CountDownLatch(1);
      WindowsTray tray =
          (WindowsTray)
              application.tray(
                  TrayIcon.builder()
                      .icon(Icons.circle(32, Color.GREEN))
                      .onActivate(activated::countDown)
                      .build());

      tray.simulateClick(User32.WM_LBUTTONUP);
      Assertions.assertTrue(activated.await(5, TimeUnit.SECONDS), "onActivate must run");
    }
  }
}
