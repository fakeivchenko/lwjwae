package dev.ivchenko.lwjwae.notification;

import dev.ivchenko.lwjwae.util.ResourceUtil;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NotificationTest {
  @Test
  void ofTakesTitleAndBody() {
    Notification notification = Notification.of("Saved", "report.pdf");

    Assertions.assertEquals("Saved", notification.title());
    Assertions.assertEquals("report.pdf", notification.body());
    Assertions.assertNull(notification.icon());
    Assertions.assertEquals(List.of(), notification.actions());
  }

  @Test
  void theIconComesFromTheResourcesAndTheButtonsOneByOne() {
    NotificationAction open = new NotificationAction("Open", () -> {});

    Notification notification =
        Notification.builder().title("Saved").icon("fixtures/dot.png").actions(open).build();

    Assertions.assertArrayEquals(ResourceUtil.read("fixtures/dot.png"), notification.icon());
    Assertions.assertEquals(List.of(open), notification.actions());
  }
}
