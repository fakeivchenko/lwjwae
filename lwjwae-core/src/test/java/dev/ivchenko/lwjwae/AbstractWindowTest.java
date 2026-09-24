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
import dev.ivchenko.lwjwae.testing.RpcReply;
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
  private static final String VALUE_TYPE = "application/x-lwjwae-value; charset=utf-8";

  @Test
  void installsTheBridgeRuntimeBeforeAnyPage() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      Assertions.assertEquals(1, window.injected.size());
      Assertions.assertTrue(window.injected.getFirst().contains("fakeHost.post"));
    }
  }

  @Test
  void rebindingWindowNameReplacesTheHandlerWithoutInjectingAgain() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.bind("answer", _ -> "first");
      window.bind("answer", _ -> "second");

      String binding = BridgeProtocol.bindingScript("answer");
      Assertions.assertEquals(1, window.injected.stream().filter(binding::equals).count());
      Assertions.assertEquals(1, window.evaluated.stream().filter(binding::equals).count());
      window.call(1, "answer", "x");
      Assertions.assertEquals("second", window.awaitReply(1).body());
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
  void typedBindingsAndObjectEventsNeedCodec() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      Assertions.assertThrows(
          IllegalStateException.class,
          () -> window.bind("typed", Point.class, Function.identity()));
      Assertions.assertThrows(
          IllegalStateException.class, () -> window.emit("tick", new Point(1, 2)));
      Assertions.assertDoesNotThrow(() -> window.emit("tick", "text"));
      window.call(1, BridgeProtocol.EVENTS_CALL, "");
      Assertions.assertEquals(List.of("0" + SEP + "tick" + SEP + "text"), window.awaitEvents(1, 1));
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

      window.call(1, "mirror", VALUE_TYPE, "1,2");
      RpcReply mirrored = window.awaitReply(1);
      Assertions.assertEquals("2,1", mirrored.body());
      Assertions.assertTrue(mirrored.contentType().startsWith("application/x-lwjwae-value"));
      window.call(2, "origin", VALUE_TYPE, "null");
      Assertions.assertEquals("0,0", window.awaitReply(2).body());

      window.emit("moved", new Point(3, 4));
      window.call(3, BridgeProtocol.EVENTS_CALL, "");
      Assertions.assertEquals(List.of("1" + SEP + "moved" + SEP + "3,4"), window.awaitEvents(3, 1));
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

      // From Java: the page gets the event in its stream, and the Java listeners get it too.
      window.call(9, BridgeProtocol.EVENTS_CALL, "");
      window.emit("tick", "one");
      Event first = heard.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(first);
      Assertions.assertEquals("tick", first.name());
      Assertions.assertEquals("one", first.payload());
      Assertions.assertFalse(first.typed());
      Assertions.assertEquals("one", texts.poll(5, TimeUnit.SECONDS));
      Assertions.assertEquals(List.of("0" + SEP + "tick" + SEP + "one"), window.awaitEvents(9, 1));

      // From the page: window.lwjwae.emit is a call to EVENT_CALL, answered with no body.
      window.call(1, BridgeProtocol.EVENT_CALL, "1" + SEP + "moved" + SEP + "3,4");
      Assertions.assertEquals(new Point(3, 4), points.poll(5, TimeUnit.SECONDS));
      Assertions.assertEquals(204, window.awaitReply(1).status());

      window.call(2, BridgeProtocol.EVENT_CALL, "0" + SEP + "tick" + SEP + "two");
      Event second = heard.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(second);
      Assertions.assertEquals("two", second.payload());
      Assertions.assertTrue(second.id() > first.id(), "IDs count deliveries");

      window.call(3, BridgeProtocol.EVENT_CALL, "garbage");
      RpcReply malformed = window.awaitReply(3);
      Assertions.assertEquals(400, malformed.status());
      Assertions.assertTrue(malformed.body().contains("Malformed event"), malformed.body());
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

      window.call(42, "reverse", "abc");

      Assertions.assertEquals("cba", window.awaitReply(42).body());
      Assertions.assertTrue(
          handlerThread.get().isVirtual(), "handlers must run on virtual threads");
    }
  }

  @Test
  void anUnknownNameIsRejectedOnThePageSide() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.call(5, "missing", "");
      RpcReply reply = window.awaitReply(5);
      Assertions.assertEquals(404, reply.status());
      Assertions.assertTrue(reply.body().contains("No handler for missing"), reply.body());
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
      window.call(9, "boom", "x");
      RpcReply reply = window.awaitReply(9);
      Assertions.assertEquals(500, reply.status());
      Assertions.assertEquals("{\"code\":\"internal\",\"error\":\"kaboom\"}", reply.body());
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
      window.call(11, "silent", "");
      Assertions.assertTrue(window.awaitReply(11).body().contains("\"IllegalStateException\""));
    }
  }

  @Test
  void payloadMayContainTheSeparator() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.bind("echo", Function.identity());
      window.call(1, "echo", "a" + SEP + "b");
      Assertions.assertEquals("a" + SEP + "b", window.awaitReply(1).body());
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
                window.receive(
                    String.join(SEP, "\u0001rpc", "stolen", "doc", "1", "echo", "", "s", "x"));
              });
      Assertions.assertEquals(2, reported.size());
      Assertions.assertTrue(
          reported.getFirst().getMessage().startsWith("Malformed bridge message"));
      Assertions.assertTrue(reported.getLast().getMessage().contains("without the window token"));
      Assertions.assertTrue(window.posted.isEmpty(), "nothing may answer: " + window.posted);
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

      window.call(3, "slow", "");
      Assertions.assertTrue(handlerStarted.await(5, TimeUnit.SECONDS));
      window.close();
      windowClosed.countDown();

      Thread.sleep(200);
      Assertions.assertTrue(window.posted.isEmpty(), "a closed window gets no answer");
    }
  }

  @Test
  void eventsWaitForTheStreamAndFollowTheNewestDocument() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.emit("tick", "before any document");
      window.call(1, BridgeProtocol.EVENTS_CALL, "");
      Assertions.assertEquals(
          List.of("0" + SEP + "tick" + SEP + "before any document"), window.awaitEvents(1, 1));

      // The document goes away and gives the stream up; what is emitted then waits for the next.
      window.cancel(1);
      window.emit("tick", "for the next document");
      window.call(2, BridgeProtocol.EVENTS_CALL, "");
      Assertions.assertEquals(
          List.of("0" + SEP + "tick" + SEP + "for the next document"), window.awaitEvents(2, 1));
      Assertions.assertEquals(1, window.awaitEvents(1, 1).size(), "the old stream gets no more");

      // A stream that nobody gave up is taken over by the next one.
      window.call(3, BridgeProtocol.EVENTS_CALL, "");
      window.awaitEnd(2);
      window.emit("tick", "for the third document");
      Assertions.assertEquals(
          List.of("0" + SEP + "tick" + SEP + "for the third document"), window.awaitEvents(3, 1));
    }
  }

  @Test
  void cancellingACallInterruptsItsHandler() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch interrupted = new CountDownLatch(1);
      window.handle(
          "wait",
          call -> {
            started.countDown();
            try {
              Thread.sleep(10_000);
            } catch (InterruptedException _) {
              if (call.isCancelled()) {
                interrupted.countDown();
              }
            }
          });
      window.call(1, "wait", "");
      Assertions.assertTrue(started.await(5, TimeUnit.SECONDS));
      window.cancel(1);
      Assertions.assertTrue(interrupted.await(5, TimeUnit.SECONDS));
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
