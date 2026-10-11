package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.event.FileDropEvent;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeWindow;
import dev.ivchenko.lwjwae.testing.RpcReply;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class PageCapabilityTest {
  private static final String SEP = BridgeProtocol.SEPARATOR;

  @Test
  void pageWithoutGrantsHasNoneOfTheCapabilities() throws Exception {
    try (FakeApplication application = new FakeApplication(ApplicationParameters.createDefault())) {
      FakeWindow window = application.openFake();
      window.call(1, BridgeProtocol.CLIPBOARD_CALL, "read-text");
      window.call(2, BridgeProtocol.STORE_CALL, "{\"op\":\"query\",\"sql\":\"SELECT 1\"}");
      window.call(3, BridgeProtocol.CONTROL_CALL, "minimize");
      window.call(4, BridgeProtocol.CONTROL_CALL, "badge" + SEP + "3");
      window.call(5, BridgeProtocol.CLOSE_CALL, "");

      for (long id = 1; id <= 5; id++) {
        RpcReply reply = window.awaitReply(id);
        Assertions.assertEquals(403, reply.status(), reply.toString());
        Assertions.assertTrue(reply.body().contains("\"code\":\"capability\""), reply.body());
      }
      Assertions.assertTrue(
          window.awaitReply(1).body().contains("PageCapability.CLIPBOARD"), "names what it needs");
      Assertions.assertFalse(window.isClosed());
      Assertions.assertFalse(window.isMinimized());
      Assertions.assertEquals(List.of(), application.shownBadgeCounts);
    }
  }

  @Test
  void pageWithoutGrantsStillReadsAndFollowsThePointer() throws Exception {
    try (FakeApplication application = new FakeApplication(ApplicationParameters.createDefault())) {
      FakeWindow window = application.openFake();
      window.call(1, BridgeProtocol.CONTROL_CALL, "theme");
      window.call(2, BridgeProtocol.CONTROL_CALL, "state");
      window.call(3, BridgeProtocol.CONTROL_CALL, "move");

      for (long id = 1; id <= 3; id++) {
        RpcReply reply = window.awaitReply(id);
        Assertions.assertEquals(2, reply.status() / 100, reply.toString());
      }
    }
  }

  @Test
  void grantedCapabilityOpensItsCallAndNoOther() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().pageCapabilities(Set.of(PageCapability.TASKBAR)).build();
    try (FakeApplication application = new FakeApplication(parameters)) {
      FakeWindow window = application.openFake();
      window.call(1, BridgeProtocol.CONTROL_CALL, "badge" + SEP + "3");
      window.call(2, BridgeProtocol.CONTROL_CALL, "minimize");

      Assertions.assertEquals(204, window.awaitReply(1).status());
      Assertions.assertEquals(403, window.awaitReply(2).status());
      Assertions.assertEquals(List.of(3), application.shownBadgeCounts);
    }
  }

  @Test
  void dropsReachJavaButNotPageWithoutFiles() throws Exception {
    try (FakeApplication application = new FakeApplication(ApplicationParameters.createDefault())) {
      FakeWindow window = application.openFake();
      BlockingQueue<FileDropEvent> heard = new LinkedBlockingQueue<>();
      window.webView().onFileDrop(heard::add);
      window.call(1, BridgeProtocol.EVENTS_CALL, "");

      window.dropFiles(List.of(Path.of("/tmp/a.txt")), 1, 2);

      Assertions.assertNotNull(heard.poll(5, TimeUnit.SECONDS), "Java hears the drop");
      window.emit("marker", "after the drop");
      Assertions.assertEquals(
          List.of("0" + SEP + "marker" + SEP + "after the drop"),
          window.awaitEvents(1, 1),
          "the page hears nothing of the drop");
    }
  }
}
