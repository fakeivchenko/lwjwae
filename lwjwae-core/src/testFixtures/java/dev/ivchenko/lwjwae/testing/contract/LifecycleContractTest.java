package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.Tags;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests failure paths and the window lifecycle: what happens when things go wrong or disappear. */
@Tag(Tags.DISPLAY)
@Timeout(60)
public abstract class LifecycleContractTest extends DisplayContractTest {
  @Test
  void unreachableHostReportsFailedLoad() throws Exception {
    // A port nothing listens on: refused at once, no network involved. Not a low port - browser
    // engines silently cancel loads to their "unsafe port" list (1, 7, 25, ...) instead of failing
    // them.
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    String url = "http://127.0.0.1:" + closedPort + "/";

    try (ApplicationBackend backend = Application.create()) {
      var failed = Loads.expectFailed(backend);
      backend.navigate(url);

      LoadEvent event = failed.get(30, TimeUnit.SECONDS);
      Assertions.assertEquals(url, event.url());
      Assertions.assertNotNull(event.message());
    }
  }

  @Test
  void missingResourceReportsFailedLoad() throws Exception {
    try (ApplicationBackend backend = Application.create()) {
      var failed = Loads.expectFailed(backend);
      backend.loadResource("test-app/does-not-exist.html");

      LoadEvent event = failed.get(30, TimeUnit.SECONDS);
      Assertions.assertTrue(
          event.message().contains("test-app/does-not-exist.html"), event.message());
    }
  }

  @Test
  void throwingScriptFailsTheFuture() throws Exception {
    try (ApplicationBackend backend = Application.create()) {
      final var loaded = Loads.expectFinished(backend);
      backend.html("<html><body></body></html>");
      loaded.get(30, TimeUnit.SECONDS);

      ExecutionException failure =
          Assertions.assertThrows(
              ExecutionException.class,
              () -> backend.eval("throw new Error('kaboom')").get(20, TimeUnit.SECONDS));
      Assertions.assertInstanceOf(ScriptEvaluationFailedException.class, failure.getCause());
    }
  }

  @Test
  void closedWindowRejectsEveryCall() {
    try (ApplicationBackend backend = Application.create()) {
      backend.show();
      backend.close();

      Assertions.assertThrows(IllegalStateException.class, backend::title);
      Assertions.assertThrows(IllegalStateException.class, () -> backend.navigate("about:blank"));
      ExecutionException failure =
          Assertions.assertThrows(
              ExecutionException.class, () -> backend.eval("1").get(20, TimeUnit.SECONDS));
      Assertions.assertInstanceOf(IllegalStateException.class, failure.getCause());

      Assertions.assertDoesNotThrow(backend::close, "close() must be idempotent");
    }
  }

  @Test
  void closingFromAnotherThreadUnblocksRun() throws Exception {
    try (ApplicationBackend backend =
        Application.create(ApplicationParameters.builder().title("run").build())) {
      Thread runner = new Thread(backend::run, "run-caller");
      runner.start();

      Thread.sleep(500);
      Assertions.assertTrue(runner.isAlive(), "run() must block while the window is open");
      backend.close();
      runner.join(10_000);
      Assertions.assertFalse(runner.isAlive(), "run() must return once the window is closed");
    }
  }

  @Test
  void twoWindowsAreIndependent() throws Exception {
    ApplicationParameters first = ApplicationParameters.builder().title("first").build();
    ApplicationParameters second = ApplicationParameters.builder().title("second").build();
    try (ApplicationBackend one = Application.create(first);
        ApplicationBackend two = Application.create(second)) {
      final var oneLoaded = Loads.expectFinished(one);
      final var twoLoaded = Loads.expectFinished(two);
      one.html("<html><head><title>one</title></head></html>");
      two.html("<html><head><title>two</title></head></html>");
      oneLoaded.get(30, TimeUnit.SECONDS);
      twoLoaded.get(30, TimeUnit.SECONDS);

      Assertions.assertEquals("one", Loads.eval(one, "document.title"));
      Assertions.assertEquals("two", Loads.eval(two, "document.title"));

      one.close();
      Assertions.assertEquals(
          "two", Loads.eval(two, "document.title"), "closing one must not touch the other");
    }
  }
}
