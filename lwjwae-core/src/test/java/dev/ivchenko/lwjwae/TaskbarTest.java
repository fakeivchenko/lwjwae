package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.taskbar.ProgressState;
import dev.ivchenko.lwjwae.taskbar.TaskbarProgress;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeWindow;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class TaskbarTest {
  private static final String SEP = BridgeProtocol.SEPARATOR;

  @Test
  void progressIsClampedAndKeptByTheApplication() {
    Assertions.assertEquals(1.0, TaskbarProgress.of(3).value());
    Assertions.assertEquals(0.0, TaskbarProgress.of(Double.NaN).value());
    Assertions.assertEquals(ProgressState.NONE, new TaskbarProgress(null, 0.5).state());
    try (FakeApplication application = new FakeApplication()) {
      Assertions.assertEquals(TaskbarProgress.none(), application.progress());
      application.progress(0.25);
      application.badgeCount(3);
      Assertions.assertEquals(TaskbarProgress.of(0.25), application.progress());
      Assertions.assertEquals(3, application.badgeCount());
      Assertions.assertEquals(List.of(TaskbarProgress.of(0.25)), application.shownProgress);
      Assertions.assertEquals(List.of(3), application.shownBadgeCounts);
      Assertions.assertThrows(IllegalArgumentException.class, () -> application.badgeCount(-1));
    }
  }

  @Test
  void thePageSetsTheProgressAndTheBadge() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.call(1, BridgeProtocol.CONTROL_CALL, String.join(SEP, "progress", "paused", "0.5"));
      Assertions.assertEquals(204, window.awaitReply(1).status());
      Assertions.assertEquals(TaskbarProgress.paused(0.5), application.progress());
      window.call(2, BridgeProtocol.CONTROL_CALL, String.join(SEP, "badge", "7"));
      Assertions.assertEquals(204, window.awaitReply(2).status());
      Assertions.assertEquals(7, application.badgeCount());
      window.call(3, BridgeProtocol.CONTROL_CALL, String.join(SEP, "progress", "loud", "1"));
      Assertions.assertEquals(400, window.awaitReply(3).status());
      window.call(4, BridgeProtocol.CONTROL_CALL, String.join(SEP, "badge", "-2"));
      Assertions.assertEquals(400, window.awaitReply(4).status());
    }
  }
}
