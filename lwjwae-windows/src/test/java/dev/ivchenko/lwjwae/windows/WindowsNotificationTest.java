package dev.ivchenko.lwjwae.windows;

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

class WindowsNotificationTest extends NotificationContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }

  @Test
  void toastStaysUpUntilItsTimeRunsOut() throws Exception {
    try (Application application = Application.create()) {
      WindowsNotification notification =
          (WindowsNotification)
              application.showNotification(
                  Notification.builder().title("lwjwae :: stays").body("No Failed event").build());

      // A toast that Windows can't show reports Failed within moments; this one must not.
      Thread.sleep(1500);
      Assertions.assertFalse(notification.isClosed(), "Windows must take and keep the toast");
      notification.close();
    }
  }

  @Test
  void clickAndButtonsRunTheirHandlers() throws Exception {
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

      WindowsNotification clicked = (WindowsNotification) application.showNotification(content);
      clicked.simulateActivation("default");
      Assertions.assertTrue(activated.await(5, TimeUnit.SECONDS), "onActivate must run");
      Assertions.assertTrue(clicked.isClosed(), "a click ends the toast");

      WindowsNotification answered = (WindowsNotification) application.showNotification(content);
      answered.simulateActivation("action-1");
      Assertions.assertTrue(second.await(5, TimeUnit.SECONDS), "the second button must run");
    }
  }

  @Test
  void dismissOnTheDesktopClosesTheHandle() {
    try (Application application = Application.create()) {
      WindowsNotification notification =
          (WindowsNotification)
              application.showNotification(
                  Notification.builder().title("lwjwae :: dismiss").build());

      notification.simulateDismiss();
      Assertions.assertTrue(notification.isClosed());
    }
  }

  @Test
  void toastThatTimesOutIsTakenBack() {
    try (Application application = Application.create()) {
      WindowsNotification notification =
          (WindowsNotification)
              application.showNotification(
                  Notification.builder().title("lwjwae :: timeout").build());

      notification.simulateTimeout();
      Assertions.assertTrue(notification.isClosed(), "a timed-out toast must not pile up");
    }
  }

  @Test
  void applicationIdsKeepNamesApart() {
    String notes = WindowsNotifier.idFor("Заметки");
    String tracker = WindowsNotifier.idFor("Трекер");
    Assertions.assertNotEquals(notes, tracker, "names in Cyrillic must not share an ID");
    Assertions.assertTrue(notes.matches("lwjwae\\.[0-9a-f]{8}"), notes);
    Assertions.assertTrue(
        WindowsNotifier.idFor("lwjwae demo").matches("lwjwae\\.lwjwae\\.demo\\.[0-9a-f]{8}"));
  }

  @Test
  void toastXmlEscapesTheText() {
    String xml =
        WindowsNotifier.xml(
            Notification.builder()
                .title("<b>&\"'")
                .actions(List.of(new NotificationAction("A & B", null)))
                .build(),
            null);
    Assertions.assertTrue(xml.contains("<text>&lt;b&gt;&amp;&quot;&apos;</text>"), xml);
    Assertions.assertTrue(xml.contains("content=\"A &amp; B\" arguments=\"action-0\""), xml);
  }
}
