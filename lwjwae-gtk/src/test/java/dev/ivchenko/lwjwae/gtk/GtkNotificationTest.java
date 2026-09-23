package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.testing.contract.NotificationContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GtkNotificationTest extends NotificationContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }

  @Test
  void clickAndButtonsRunTheirHandlers() throws Exception {
    try (Application application = Application.create()) {
      CountDownLatch activated = new CountDownLatch(1);
      CountDownLatch second = new CountDownLatch(1);
      GtkNotification notification =
          (GtkNotification)
              application.showNotification(
                  Notification.builder()
                      .title("lwjwae :: actions")
                      .actions(
                          List.of(
                              new NotificationAction("First", () -> {}),
                              new NotificationAction("Second", second::countDown)))
                      .onActivate(activated::countDown)
                      .build());

      notification.simulateAction("default");
      notification.simulateAction("action-1");
      Assertions.assertTrue(activated.await(5, TimeUnit.SECONDS), "onActivate must run");
      Assertions.assertTrue(second.await(5, TimeUnit.SECONDS), "the second button must run");
      notification.close();
    }
  }

  @Test
  void dismissOnTheDesktopClosesTheHandle() {
    try (Application application = Application.create()) {
      GtkNotification notification =
          (GtkNotification)
              application.showNotification(
                  Notification.builder().title("lwjwae :: dismiss").build());

      notification.simulateDismiss();
      Assertions.assertTrue(notification.isClosed());
    }
  }
}
