package dev.ivchenko.lwjwae.update;

import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeWindow;
import dev.ivchenko.lwjwae.testing.PageGrants;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class UpdatePageTest {
  /** The page call of {@code window.lwjwae.updates}. */
  private static final String CALL = "lwjwae:update";

  @Test
  void thePageChecksForAnUpdateThatThisProcessCanNotInstall() throws Exception {
    try (UpdateServer server = new UpdateServer()) {
      try (FakeApplication application =
          FakeApplication.withModules(PageGrants.every(ApplicationParameters.builder().build()))) {
        Updater.of(
            application,
            new UpdateParameters(server.url("/manifest.json"), server.publicKey(), "1.0.0"));
        FakeWindow window = application.openFake();
        window.call(1, CALL, "check");
        Assertions.assertEquals(502, window.awaitReply(1).status(), "no manifest yet");

        server.release("1.1.0", "1.0.0", "new".getBytes(StandardCharsets.UTF_8));
        window.call(2, CALL, "check");
        Assertions.assertEquals(
            "{\"version\":\"1.1.0\",\"notes\":\"Faster\",\"mandatory\":false,"
                + "\"installable\":false,\"size\":3}",
            window.awaitReply(2).body());
        window.call(3, CALL, "install");
        Assertions.assertEquals(409, window.awaitReply(3).status(), "not from an AppImage");

        server.release("1.0.0", "1.0.0", "old".getBytes(StandardCharsets.UTF_8));
        window.call(4, CALL, "check");
        Assertions.assertEquals("", window.awaitReply(4).body());
        window.call(5, CALL, "install");
        Assertions.assertEquals(404, window.awaitReply(5).status());
        Assertions.assertFalse(application.isClosed());
      }
    }
  }

  @Test
  void applicationWithoutUpdateParametersHasNoUpdater() {
    try (FakeApplication application =
        FakeApplication.withModules(ApplicationParameters.builder().build())) {
      Assertions.assertThrows(IllegalStateException.class, () -> Updater.of(application));
    }
  }

  @Test
  void pagesFindTheUpdatesOfTheModule() {
    try (FakeApplication application =
        FakeApplication.withModules(ApplicationParameters.builder().build())) {
      Assertions.assertTrue(
          application.openFake().injected.getFirst().contains("\"updates\": ("),
          "the bootstrap carries the page API of the module");
    }
  }
}
