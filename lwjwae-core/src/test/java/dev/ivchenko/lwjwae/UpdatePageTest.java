package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeWindow;
import dev.ivchenko.lwjwae.update.UpdateParameters;
import dev.ivchenko.lwjwae.update.UpdateServer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class UpdatePageTest {
  @Test
  void thePageChecksForAnUpdateThatThisProcessCanNotInstall() throws Exception {
    try (UpdateServer server = new UpdateServer()) {
      ApplicationParameters parameters =
          ApplicationParameters.builder()
              .updates(
                  new UpdateParameters(server.url("/manifest.json"), server.publicKey(), "1.0.0"))
              .build();
      try (FakeApplication application = new FakeApplication(parameters)) {
        FakeWindow window = application.openFake();
        window.call(1, BridgeProtocol.UPDATE_CALL, "check");
        Assertions.assertEquals(502, window.awaitReply(1).status(), "no manifest yet");

        server.release("1.1.0", "1.0.0", "new".getBytes(StandardCharsets.UTF_8));
        window.call(2, BridgeProtocol.UPDATE_CALL, "check");
        Assertions.assertEquals(
            "{\"version\":\"1.1.0\",\"notes\":\"Faster\",\"mandatory\":false,"
                + "\"installable\":false,\"size\":3}",
            window.awaitReply(2).body());
        window.call(3, BridgeProtocol.UPDATE_CALL, "install");
        Assertions.assertEquals(409, window.awaitReply(3).status(), "not from an AppImage");

        server.release("1.0.0", "1.0.0", "old".getBytes(StandardCharsets.UTF_8));
        window.call(4, BridgeProtocol.UPDATE_CALL, "check");
        Assertions.assertEquals("", window.awaitReply(4).body());
        window.call(5, BridgeProtocol.UPDATE_CALL, "install");
        Assertions.assertEquals(404, window.awaitReply(5).status());
        Assertions.assertFalse(application.isClosed());
      }
    }
  }

  @Test
  void applicationWithoutUpdateParametersHasNoUpdater() {
    try (FakeApplication application = new FakeApplication()) {
      Assertions.assertThrows(IllegalStateException.class, application::updater);
    }
  }
}
