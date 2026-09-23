package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.testing.Icons;
import dev.ivchenko.lwjwae.testing.Tags;
import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Shows notifications on a real desktop and takes them back through their handles.
 *
 * <p>No test can see the notification or click it, so what the contract checks is that the desktop
 * takes every kind of notification, that a handle closes on its own and with its application, and
 * that a notification doesn't hold the application up. Clicks go through the hooks of each backend,
 * in the tests of that backend.
 */
@Tag(Tags.DISPLAY)
@Timeout(60)
public abstract class NotificationContractTest extends DisplayContractTest {
  private static final byte[] GREEN = Icons.circle(64, new Color(0x34, 0xd3, 0x99));

  @Test
  void desktopTakesEveryPartAndTheHandleTakesItBack() throws Exception {
    try (Application application = Application.create()) {
      NotificationHandle notification =
          application.showNotification(
              Notification.builder()
                  .title("lwjwae :: notification")
                  .body("With an image, two buttons, and a handler for the click")
                  .icon(GREEN)
                  .actions(
                      List.of(
                          new NotificationAction("First", () -> {}),
                          new NotificationAction("Second", null)))
                  .onActivate(() -> {})
                  .build());
      Assertions.assertFalse(notification.isClosed());
      Thread.sleep(300);

      notification.close();
      Assertions.assertTrue(notification.isClosed());
      notification.close();
    }
  }

  @Test
  void titleAloneIsEnough() {
    try (Application application = Application.create()) {
      NotificationHandle notification =
          application.showNotification(Notification.builder().title("lwjwae :: title").build());
      notification.close();
      Assertions.assertTrue(notification.isClosed());
    }
  }

  @Test
  void notificationGoesWithItsApplication() {
    NotificationHandle notification;
    try (Application application = Application.create()) {
      notification =
          application.showNotification(Notification.builder().title("lwjwae :: quit").build());
      Assertions.assertFalse(notification.isClosed());
    }
    Assertions.assertTrue(notification.isClosed(), "The notification must go with its application");
  }
}
