package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.macos.binding.UserNotifications;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.testing.contract.NotificationContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MacNotificationTest extends NotificationContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }

  /**
   * The test JVM runs from the {@code java} launcher, which isn't an application bundle, and a Mac
   * without a user at it can't grant the permission either; the contract runs where both hold.
   */
  @Override
  protected void assumeNotificationsAllowed() {
    try (Application application = Application.create()) {
      application.showNotification(Notification.builder().title("lwjwae :: probe").build()).close();
    } catch (UnsupportedOperationException e) {
      Assumptions.abort(e.getMessage());
    }
  }

  /** Runs everywhere, bundle or not: the answer is a handle or a clean refusal, never a crash. */
  @Test
  void outsideBundleTheRefusalIsClean() {
    try (Application application = Application.create()) {
      boolean bundled = MacDispatcher.instance().call(UserNotifications::isBundledApplication);
      try {
        NotificationHandle notification =
            application.showNotification(Notification.builder().title("lwjwae :: bundle").build());
        Assertions.assertTrue(bundled, "only a bundle gets a notification center");
        notification.close();
      } catch (UnsupportedOperationException e) {
        Assertions.assertNotNull(e.getMessage());
      }
    }
  }

  @Test
  void clickAndButtonsRunTheirHandlers() throws Exception {
    this.assumeNotificationsAllowed();
    try (Application application = Application.create()) {
      CountDownLatch activated = new CountDownLatch(1);
      CountDownLatch second = new CountDownLatch(1);
      Notification content =
          Notification.builder()
              .title("lwjwae :: actions")
              .actions(
                  List.of(
                      new NotificationAction("First", () -> {}),
                      new NotificationAction("Second", second::countDown)))
              .onActivate(activated::countDown)
              .build();

      MacNotification clicked = (MacNotification) application.showNotification(content);
      clicked.simulateResponse(UserNotifications.DEFAULT_ACTION);
      Assertions.assertTrue(activated.await(5, TimeUnit.SECONDS), "onActivate must run");
      Assertions.assertTrue(clicked.isClosed(), "a click ends the notification");

      MacNotification answered = (MacNotification) application.showNotification(content);
      answered.simulateResponse("action-1");
      Assertions.assertTrue(second.await(5, TimeUnit.SECONDS), "the second button must run");

      MacNotification dismissed = (MacNotification) application.showNotification(content);
      dismissed.simulateResponse(UserNotifications.DISMISS_ACTION);
      Assertions.assertTrue(dismissed.isClosed(), "a dismissal ends the notification");
    }
  }
}
