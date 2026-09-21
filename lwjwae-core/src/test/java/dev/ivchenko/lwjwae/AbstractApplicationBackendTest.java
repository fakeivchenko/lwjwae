package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.testing.FakeApplicationBackend;
import dev.ivchenko.lwjwae.testing.Point;
import dev.ivchenko.lwjwae.testing.PointCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests the toolkit-independent half of a backend through {@link FakeApplicationBackend}. */
@Timeout(10)
class AbstractApplicationBackendTest {
  private static final String SEP = BridgeProtocol.SEPARATOR;

  @Test
  void installsTheBridgeRuntimeBeforeAnyPage() {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      Assertions.assertEquals(1, backend.injected.size());
      Assertions.assertTrue(backend.injected.getFirst().contains("fakeHost.post"));
    }
  }

  @Test
  void bindInjectsForFutureDocumentsAndEvaluatesForTheCurrentOne() {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      backend.bind("echo", Function.identity());

      String binding = BridgeProtocol.bindingScript("echo");
      Assertions.assertTrue(backend.injected.contains(binding));
      Assertions.assertTrue(backend.evaluated.contains(binding));
    }
  }

  @Test
  void typedBindingsAndObjectEventsNeedCodec() {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      Assertions.assertThrows(
          IllegalStateException.class,
          () -> backend.bind("typed", Point.class, Function.identity()));
      Assertions.assertThrows(
          IllegalStateException.class, () -> backend.emit("tick", new Point(1, 2)));
      Assertions.assertDoesNotThrow(() -> backend.emit("tick", "text"));
      Assertions.assertTrue(
          backend.evaluated.contains(BridgeProtocol.emitScript("tick", "text", false)));
    }
  }

  @Test
  void typedBindingsGoThroughTheCodec() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().codec(new PointCodec()).build();
    try (FakeApplicationBackend backend = new FakeApplicationBackend(parameters)) {
      backend.bind("mirror", Point.class, point -> new Point(point.y(), point.x()));
      backend.bind("origin", Void.class, _ -> new Point(0, 0));
      Assertions.assertTrue(
          backend.injected.contains(BridgeProtocol.bindingScript("mirror", true)));

      backend.receive("1" + SEP + "mirror" + SEP + "1,2");
      awaitEvaluation(backend, BridgeProtocol.resolveScript(1, "2,1"));
      backend.receive("2" + SEP + "origin" + SEP + "null");
      awaitEvaluation(backend, BridgeProtocol.resolveScript(2, "0,0"));

      backend.emit("moved", new Point(3, 4));
      Assertions.assertTrue(
          backend.evaluated.contains(BridgeProtocol.emitScript("moved", "3,4", true)));
    }
  }

  @Test
  void thePageHalfOfTheCodecGoesIntoTheBootstrap() {
    try (FakeApplicationBackend plain = new FakeApplicationBackend()) {
      Assertions.assertTrue(plain.injected.getFirst().contains("const codec = null;"));
    }
    ApplicationParameters parameters =
        ApplicationParameters.builder().codec(new PointCodec()).build();
    try (FakeApplicationBackend custom = new FakeApplicationBackend(parameters)) {
      Assertions.assertTrue(custom.injected.getFirst().contains(new PointCodec().pageScript()));
      Assertions.assertFalse(custom.injected.getFirst().contains("const codec = null;"));
    }
  }

  @Test
  void bindRejectsNamesThatAreNotIdentifiers() {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      Function<String, String> handler = Function.identity();
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> backend.bind("not valid", handler));
      Assertions.assertThrows(IllegalArgumentException.class, () -> backend.bind("1st", handler));
      Assertions.assertThrows(IllegalArgumentException.class, () -> backend.bind("a.b", handler));
      Assertions.assertDoesNotThrow(() -> backend.bind("$_ok9", handler));
    }
  }

  @Test
  void messageReachesItsHandlerAndResolvesThePromise() throws Exception {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      AtomicReference<Thread> handlerThread = new AtomicReference<>();
      backend.bind(
          "reverse",
          payload -> {
            handlerThread.set(Thread.currentThread());
            return new StringBuilder(payload).reverse().toString();
          });

      backend.receive("42" + SEP + "reverse" + SEP + "abc");

      awaitEvaluation(backend, BridgeProtocol.resolveScript(42, "cba"));
      Assertions.assertTrue(
          handlerThread.get().isVirtual(), "handlers must run on virtual threads");
    }
  }

  @Test
  void anUnknownNameIsRejectedOnThePageSide() throws Exception {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      backend.receive("5" + SEP + "missing" + SEP);
      awaitEvaluation(backend, BridgeProtocol.rejectScript(5, "No handler bound for missing"));
    }
  }

  @Test
  void throwingHandlerRejectsWithItsMessage() throws Exception {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      backend.bind(
          "boom",
          _ -> {
            throw new IllegalStateException("kaboom");
          });
      backend.receive("9" + SEP + "boom" + SEP + "x");
      awaitEvaluation(backend, BridgeProtocol.rejectScript(9, "kaboom"));
    }
  }

  @Test
  void handlerFailureWithoutMessageRejectsWithTheTypeName() throws Exception {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      backend.bind(
          "silent",
          _ -> {
            throw new IllegalStateException();
          });
      backend.receive("11" + SEP + "silent" + SEP);
      awaitEvaluation(backend, BridgeProtocol.rejectScript(11, "IllegalStateException"));
    }
  }

  @Test
  void payloadMayContainTheSeparator() throws Exception {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      backend.bind("echo", Function.identity());
      backend.receive("1" + SEP + "echo" + SEP + "a" + SEP + "b");
      awaitEvaluation(backend, BridgeProtocol.resolveScript(1, "a" + SEP + "b"));
    }
  }

  @Test
  void malformedMessagesAreReportedNotThrown() {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      List<Throwable> reported =
          captureUncaught(
              () -> {
                backend.receive("no separators here");
                backend.receive("x" + SEP + "name" + SEP + "payload");
              });
      Assertions.assertEquals(2, reported.size());
      Assertions.assertTrue(
          reported.getFirst().getMessage().startsWith("Malformed bridge message"));
      Assertions.assertTrue(
          backend.evaluated.stream().noneMatch(script -> script.contains("settle")));
    }
  }

  @Test
  void noAnswerIsSentToClosedWindow() throws Exception {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      CountDownLatch handlerStarted = new CountDownLatch(1);
      CountDownLatch windowClosed = new CountDownLatch(1);
      backend.bind(
          "slow",
          _ -> {
            handlerStarted.countDown();
            await(windowClosed);
            return "late";
          });

      backend.receive("3" + SEP + "slow" + SEP);
      Assertions.assertTrue(handlerStarted.await(5, TimeUnit.SECONDS));
      backend.close();
      windowClosed.countDown();

      Thread.sleep(200);
      Assertions.assertFalse(backend.evaluated.contains(BridgeProtocol.resolveScript(3, "late")));
    }
  }

  @Test
  void loadResourceServesFromTheJarByDefault() {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      backend.loadResource("app/index.html");
      Assertions.assertEquals(List.of("app://local/app/index.html"), backend.navigated);
    }
  }

  @Test
  void loadResourceGoesToTheDevServerWhenConfigured() {
    ApplicationParameters parameters =
        ApplicationParameters.builder().devServerUrl("http://localhost:5173").build();
    try (FakeApplicationBackend backend = new FakeApplicationBackend(parameters)) {
      backend.loadResource("app/index.html");
      Assertions.assertEquals(List.of("http://localhost:5173"), backend.navigated);
    }
  }

  @Test
  void everyListenerHearsEveryEventEvenIfOneThrows() {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      List<LoadEvent> heard = new ArrayList<>();
      backend.onLoad(
          _ -> {
            throw new RuntimeException("bad listener");
          });
      backend.onLoad(heard::add);

      LoadEvent event = LoadEvent.of(LoadState.FINISHED, "app://local/x");
      List<Throwable> reported = captureUncaught(() -> backend.emit(event));

      Assertions.assertEquals(List.of(event), heard);
      Assertions.assertEquals("bad listener", reported.getFirst().getMessage());
    }
  }

  @Test
  void runBlocksUntilTheWindowCloses() throws Exception {
    try (FakeApplicationBackend backend = new FakeApplicationBackend()) {
      Thread runner = new Thread(backend::run);
      runner.start();

      Thread.sleep(100);
      Assertions.assertTrue(runner.isAlive(), "run() must block while the window is open");
      backend.close();
      runner.join(5000);
      Assertions.assertFalse(runner.isAlive());
      Assertions.assertTrue(backend.isClosed());
    }
  }

  private static void awaitEvaluation(FakeApplicationBackend backend, String script)
      throws Exception {
    for (int i = 0; i < 50; i++) {
      if (backend.evaluated.contains(script)) {
        return;
      }
      Thread.sleep(50);
    }
    Assertions.fail("Expected evaluation of " + script + " but saw " + backend.evaluated);
  }

  private static List<Throwable> captureUncaught(Runnable action) {
    List<Throwable> reported = new ArrayList<>();
    Thread current = Thread.currentThread();
    Thread.UncaughtExceptionHandler previous = current.getUncaughtExceptionHandler();
    current.setUncaughtExceptionHandler((_, t) -> reported.add(t));
    try {
      action.run();
    } finally {
      current.setUncaughtExceptionHandler(previous);
    }
    return reported;
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }
}
