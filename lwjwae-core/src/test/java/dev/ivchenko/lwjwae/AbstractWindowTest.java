package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeWindow;
import dev.ivchenko.lwjwae.testing.Point;
import dev.ivchenko.lwjwae.testing.PointCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests the toolkit-independent half of a window through {@link FakeWindow}. */
@Timeout(10)
class AbstractWindowTest {
  private static final String SEP = BridgeProtocol.SEPARATOR;

  @Test
  void installsTheBridgeRuntimeBeforeAnyPage() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      Assertions.assertEquals(1, window.injected.size());
      Assertions.assertTrue(window.injected.getFirst().contains("fakeHost.post"));
    }
  }

  @Test
  void bindInjectsForFutureDocumentsAndEvaluatesForTheCurrentOne() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.bind("echo", Function.identity());

      String binding = BridgeProtocol.bindingScript("echo");
      Assertions.assertTrue(window.injected.contains(binding));
      Assertions.assertTrue(window.evaluated.contains(binding));
    }
  }

  @Test
  void typedBindingsAndObjectEventsNeedCodec() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      Assertions.assertThrows(
          IllegalStateException.class,
          () -> window.bind("typed", Point.class, Function.identity()));
      Assertions.assertThrows(
          IllegalStateException.class, () -> window.emit("tick", new Point(1, 2)));
      Assertions.assertDoesNotThrow(() -> window.emit("tick", "text"));
      Assertions.assertTrue(
          window.evaluated.contains(BridgeProtocol.emitScript("tick", "text", false)));
    }
  }

  @Test
  void typedBindingsGoThroughTheCodec() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().codec(new PointCodec()).build();
    try (FakeApplication application = new FakeApplication(parameters)) {
      FakeWindow window = application.openFake();
      window.bind("mirror", Point.class, point -> new Point(point.y(), point.x()));
      window.bind("origin", Void.class, _ -> new Point(0, 0));
      Assertions.assertTrue(window.injected.contains(BridgeProtocol.bindingScript("mirror", true)));

      window.receive("1" + SEP + "mirror" + SEP + "1,2");
      window.awaitEvaluation(BridgeProtocol.resolveScript(1, "2,1"));
      window.receive("2" + SEP + "origin" + SEP + "null");
      window.awaitEvaluation(BridgeProtocol.resolveScript(2, "0,0"));

      window.emit("moved", new Point(3, 4));
      Assertions.assertTrue(
          window.evaluated.contains(BridgeProtocol.emitScript("moved", "3,4", true)));
    }
  }

  @Test
  void javaListenersHearEventsFromJavaAndFromThePage() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().codec(new PointCodec()).build();
    try (FakeApplication application = new FakeApplication(parameters)) {
      FakeWindow window = application.openFake();
      BlockingQueue<Event> heard = new LinkedBlockingQueue<>();
      BlockingQueue<Point> points = new LinkedBlockingQueue<>();
      BlockingQueue<String> texts = new LinkedBlockingQueue<>();
      window.listen("tick", heard::add);
      window.listen("moved", Point.class, points::add);
      window.listen("tick", String.class, texts::add);

      // From Java: the page gets the script, and the Java listeners get the event.
      window.emit("tick", "one");
      Event first = heard.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(first);
      Assertions.assertEquals("tick", first.name());
      Assertions.assertEquals("one", first.payload());
      Assertions.assertFalse(first.typed());
      Assertions.assertEquals("one", texts.poll(5, TimeUnit.SECONDS));
      Assertions.assertTrue(
          window.evaluated.contains(BridgeProtocol.emitScript("tick", "one", false)));

      // From the page: window.lwjwae.emit posts an EVENT_CALL message; the promise resolves.
      window.receive(
          "1" + SEP + BridgeProtocol.EVENT_CALL + SEP + "1" + SEP + "moved" + SEP + "3,4");
      Assertions.assertEquals(new Point(3, 4), points.poll(5, TimeUnit.SECONDS));
      window.awaitEvaluation(BridgeProtocol.resolveScript(1, ""));

      window.receive(
          "2" + SEP + BridgeProtocol.EVENT_CALL + SEP + "0" + SEP + "tick" + SEP + "two");
      Event second = heard.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(second);
      Assertions.assertEquals("two", second.payload());
      Assertions.assertTrue(second.id() > first.id(), "IDs count deliveries");

      window.receive("3" + SEP + BridgeProtocol.EVENT_CALL + SEP + "garbage");
      window.awaitEvaluation(BridgeProtocol.rejectScript(3, "Malformed event"));
    }
  }

  @Test
  void onceHearsOneEventAndUnlistenStopsTheRest() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      BlockingQueue<String> once = new LinkedBlockingQueue<>();
      BlockingQueue<String> always = new LinkedBlockingQueue<>();
      window.once("tick", event -> once.add(event.payload()));
      final EventSubscription subscription =
          window.listen("tick", event -> always.add(event.payload()));

      window.emit("tick", "a");
      window.emit("tick", "b");
      Assertions.assertEquals("a", once.poll(5, TimeUnit.SECONDS));
      Assertions.assertEquals("a", always.poll(5, TimeUnit.SECONDS));
      Assertions.assertEquals("b", always.poll(5, TimeUnit.SECONDS));
      Assertions.assertNull(once.poll(200, TimeUnit.MILLISECONDS), "once must not hear twice");

      subscription.unlisten();
      subscription.unlisten();
      window.emit("tick", "c");
      Assertions.assertNull(always.poll(200, TimeUnit.MILLISECONDS), "unlisten must stop delivery");
    }
  }

  @Test
  void throwingListenerDoesNotStopTheOthers() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      BlockingQueue<String> heard = new LinkedBlockingQueue<>();
      window.listen(
          "tick",
          _ -> {
            throw new IllegalStateException("listener failed");
          });
      window.listen("tick", event -> heard.add(event.payload()));
      window.emit("tick", "still delivered");
      Assertions.assertEquals("still delivered", heard.poll(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void thePageHalfOfTheCodecGoesIntoTheBootstrap() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow plain = application.openFake();
      Assertions.assertTrue(plain.injected.getFirst().contains("const codec = null;"));
    }
    ApplicationParameters parameters =
        ApplicationParameters.builder().codec(new PointCodec()).build();
    try (FakeApplication application = new FakeApplication(parameters)) {
      FakeWindow custom = application.openFake();
      Assertions.assertTrue(custom.injected.getFirst().contains(new PointCodec().pageScript()));
      Assertions.assertFalse(custom.injected.getFirst().contains("const codec = null;"));
    }
  }

  @Test
  void bindRejectsNamesThatAreNotIdentifiers() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      Function<String, String> handler = Function.identity();
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> window.bind("not valid", handler));
      Assertions.assertThrows(IllegalArgumentException.class, () -> window.bind("1st", handler));
      Assertions.assertThrows(IllegalArgumentException.class, () -> window.bind("a.b", handler));
      Assertions.assertDoesNotThrow(() -> window.bind("$_ok9", handler));
    }
  }

  @Test
  void messageReachesItsHandlerAndResolvesThePromise() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      AtomicReference<Thread> handlerThread = new AtomicReference<>();
      window.bind(
          "reverse",
          payload -> {
            handlerThread.set(Thread.currentThread());
            return new StringBuilder(payload).reverse().toString();
          });

      window.receive("42" + SEP + "reverse" + SEP + "abc");

      window.awaitEvaluation(BridgeProtocol.resolveScript(42, "cba"));
      Assertions.assertTrue(
          handlerThread.get().isVirtual(), "handlers must run on virtual threads");
    }
  }

  @Test
  void anUnknownNameIsRejectedOnThePageSide() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.receive("5" + SEP + "missing" + SEP);
      window.awaitEvaluation(BridgeProtocol.rejectScript(5, "No handler bound for missing"));
    }
  }

  @Test
  void throwingHandlerRejectsWithItsMessage() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.bind(
          "boom",
          _ -> {
            throw new IllegalStateException("kaboom");
          });
      window.receive("9" + SEP + "boom" + SEP + "x");
      window.awaitEvaluation(BridgeProtocol.rejectScript(9, "kaboom"));
    }
  }

  @Test
  void handlerFailureWithoutMessageRejectsWithTheTypeName() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.bind(
          "silent",
          _ -> {
            throw new IllegalStateException();
          });
      window.receive("11" + SEP + "silent" + SEP);
      window.awaitEvaluation(BridgeProtocol.rejectScript(11, "IllegalStateException"));
    }
  }

  @Test
  void payloadMayContainTheSeparator() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.bind("echo", Function.identity());
      window.receive("1" + SEP + "echo" + SEP + "a" + SEP + "b");
      window.awaitEvaluation(BridgeProtocol.resolveScript(1, "a" + SEP + "b"));
    }
  }

  @Test
  void malformedMessagesAreReportedNotThrown() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      List<Throwable> reported =
          captureUncaught(
              () -> {
                window.receive("no separators here");
                window.receive("x" + SEP + "name" + SEP + "payload");
              });
      Assertions.assertEquals(2, reported.size());
      Assertions.assertTrue(
          reported.getFirst().getMessage().startsWith("Malformed bridge message"));
      Assertions.assertTrue(
          window.evaluated.stream().noneMatch(script -> script.contains("settle")));
    }
  }

  @Test
  void noAnswerIsSentToClosedWindow() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      CountDownLatch handlerStarted = new CountDownLatch(1);
      CountDownLatch windowClosed = new CountDownLatch(1);
      window.bind(
          "slow",
          _ -> {
            handlerStarted.countDown();
            await(windowClosed);
            return "late";
          });

      window.receive("3" + SEP + "slow" + SEP);
      Assertions.assertTrue(handlerStarted.await(5, TimeUnit.SECONDS));
      window.close();
      windowClosed.countDown();

      Thread.sleep(200);
      Assertions.assertFalse(window.evaluated.contains(BridgeProtocol.resolveScript(3, "late")));
    }
  }

  @Test
  void loadResourceServesFromTheJarByDefault() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.loadResource("app/index.html");
      Assertions.assertEquals(List.of("app://local/app/index.html"), window.navigated);
    }
  }

  @Test
  void loadResourceGoesToTheDevServerWhenConfigured() {
    ApplicationParameters parameters =
        ApplicationParameters.builder().devServerUrl("http://localhost:5173").build();
    try (FakeApplication application = new FakeApplication(parameters)) {
      FakeWindow window = application.openFake();
      window.loadResource("app/index.html");
      Assertions.assertEquals(List.of("http://localhost:5173"), window.navigated);
    }
  }

  @Test
  void everyListenerHearsEveryEventEvenIfOneThrows() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      List<LoadEvent> heard = new ArrayList<>();
      window.onLoad(
          _ -> {
            throw new RuntimeException("bad listener");
          });
      window.onLoad(heard::add);

      LoadEvent event = LoadEvent.of(LoadState.FINISHED, "app://local/x");
      List<Throwable> reported = captureUncaught(() -> window.emit(event));

      Assertions.assertEquals(List.of(event), heard);
      Assertions.assertEquals("bad listener", reported.getFirst().getMessage());
    }
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
