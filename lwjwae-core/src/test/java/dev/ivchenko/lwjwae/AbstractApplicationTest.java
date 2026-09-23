package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeWindow;
import dev.ivchenko.lwjwae.testing.Point;
import dev.ivchenko.lwjwae.testing.PointCodec;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests the toolkit-independent half of an application through {@link FakeApplication}. */
@Timeout(10)
class AbstractApplicationTest {
  private static final String SEP = BridgeProtocol.SEPARATOR;

  @Test
  void openListsWindowsOldestFirstAndCloseDropsThem() {
    try (FakeApplication application = new FakeApplication()) {
      Assertions.assertTrue(application.windows().isEmpty());

      FakeWindow first = application.openFake(WindowParameters.builder().title("one").build());
      FakeWindow second = application.openFake();
      Assertions.assertEquals(List.of(first, second), application.windows());
      Assertions.assertNotEquals(first.id(), second.id());
      Assertions.assertSame(application, first.application());
      Assertions.assertEquals("one", first.title());
      Assertions.assertSame(first, application.window(first.id()).orElseThrow());

      first.close();
      Assertions.assertEquals(List.of(second), application.windows());
      Assertions.assertTrue(application.window(first.id()).isEmpty());
      Assertions.assertFalse(application.isClosed(), "closing a window doesn't quit");
    }
  }

  @Test
  void openNavigatesToTheUrlOrTheResource() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow blank = application.openFake();
      Assertions.assertTrue(blank.navigated.isEmpty());

      FakeWindow url =
          application.openFake(WindowParameters.builder().url("https://example.com").build());
      Assertions.assertEquals(List.of("https://example.com"), url.navigated);

      FakeWindow resource =
          application.openFake(
              WindowParameters.builder().url("https://example.com").resource("app/x.html").build());
      Assertions.assertEquals(List.of("app://local/app/x.html"), resource.navigated);
    }
  }

  @Test
  void quitClosesEveryWindowAndRefusesNewOnes() {
    FakeApplication application = new FakeApplication();
    FakeWindow first = application.openFake();
    FakeWindow second = application.openFake();

    application.quit();
    Assertions.assertTrue(first.isClosed());
    Assertions.assertTrue(second.isClosed());
    Assertions.assertTrue(application.isClosed());
    Assertions.assertTrue(application.windows().isEmpty());
    Assertions.assertThrows(IllegalStateException.class, application::open);
    Assertions.assertDoesNotThrow(application::quit, "quit() must be idempotent");
    Assertions.assertDoesNotThrow(application::close, "close() is quit()");
  }

  @Test
  void runReturnsWhenTheLastWindowClosesOrOnQuit() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      Assertions.assertDoesNotThrow(application::run, "no window: nothing to wait for");

      final FakeWindow first = application.openFake();
      Thread runner = new Thread(application::run);
      runner.start();
      Thread.sleep(100);
      Assertions.assertTrue(runner.isAlive(), "run() must block while a window is open");

      final FakeWindow second = application.openFake();
      first.close();
      Thread.sleep(100);
      Assertions.assertTrue(runner.isAlive(), "run() must block while any window is open");

      second.close();
      runner.join(5000);
      Assertions.assertFalse(runner.isAlive(), "run() must return once every window closed");

      application.openFake();
      Thread quitter = new Thread(application::run);
      quitter.start();
      Thread.sleep(100);
      Assertions.assertTrue(quitter.isAlive());
      application.quit();
      quitter.join(5000);
      Assertions.assertFalse(quitter.isAlive(), "quit() must release run()");
    }
  }

  @Test
  void applicationBindingsReachExistingAndFutureWindows() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow before = application.openFake();
      application.bind("whoami", (window, payload) -> window.id() + ":" + payload);
      FakeWindow after = application.openFake();

      String binding = BridgeProtocol.bindingScript("whoami");
      Assertions.assertTrue(before.injected.contains(binding));
      Assertions.assertTrue(before.evaluated.contains(binding), "the current document gets it");
      Assertions.assertTrue(after.injected.contains(binding), "a later window gets it");

      before.receive("1" + SEP + "whoami" + SEP + "x");
      before.awaitEvaluation(BridgeProtocol.resolveScript(1, before.id() + ":x"));
      after.receive("2" + SEP + "whoami" + SEP + "y");
      after.awaitEvaluation(BridgeProtocol.resolveScript(2, after.id() + ":y"));

      // A window-level binding of the same name wins in its window only.
      before.bind("whoami", _ -> "mine");
      before.receive("3" + SEP + "whoami" + SEP + "x");
      before.awaitEvaluation(BridgeProtocol.resolveScript(3, "mine"));
      after.receive("4" + SEP + "whoami" + SEP + "y");
      after.awaitEvaluation(BridgeProtocol.resolveScript(4, after.id() + ":y"));
    }
  }

  @Test
  void typedApplicationBindingsGoThroughTheCodec() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().codec(new PointCodec()).build();
    try (FakeApplication application = new FakeApplication(parameters)) {
      application.bind("mirror", Point.class, point -> new Point(point.y(), point.x()));
      application.bind("origin", Void.class, (window, _) -> new Point((int) window.id(), 0));
      FakeWindow window = application.openFake();
      Assertions.assertTrue(window.injected.contains(BridgeProtocol.bindingScript("mirror", true)));

      window.receive("1" + SEP + "mirror" + SEP + "1,2");
      window.awaitEvaluation(BridgeProtocol.resolveScript(1, "2,1"));
      window.receive("2" + SEP + "origin" + SEP + "null");
      window.awaitEvaluation(BridgeProtocol.resolveScript(2, window.id() + ",0"));
    }
  }

  @Test
  void windowBindingKeepsItsPageFormWhateverTheOrder() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().codec(new PointCodec()).build();
    try (FakeApplication application = new FakeApplication(parameters)) {
      String typed = BridgeProtocol.bindingScript("save", true);
      final String untyped = BridgeProtocol.bindingScript("save", false);

      // Window first: the application's typed script must not reach the window's page.
      FakeWindow own = application.openFake();
      final FakeWindow other = application.openFake();
      own.bind("save", text -> "saved " + text);
      application.bind("save", Point.class, point -> point);
      Assertions.assertFalse(own.injected.contains(typed), "would switch the page to typed");
      Assertions.assertFalse(own.evaluated.contains(typed), "would switch the current document");
      Assertions.assertTrue(other.injected.contains(typed), "other windows still get it");
      own.receive("1" + SEP + "save" + SEP + "text");
      own.awaitEvaluation(BridgeProtocol.resolveScript(1, "saved text"));

      // Application first: the window's script comes later, so it's the one the page runs.
      FakeWindow later = application.openFake();
      later.bind("save", text -> "later " + text);
      Assertions.assertEquals(untyped, later.injected.getLast());
      Assertions.assertEquals(untyped, later.evaluated.getLast());
      later.receive("2" + SEP + "save" + SEP + "text");
      later.awaitEvaluation(BridgeProtocol.resolveScript(2, "later text"));
    }
  }

  @Test
  void typedApplicationBindingsAndEventsNeedCodec() {
    try (FakeApplication application = new FakeApplication()) {
      Assertions.assertThrows(
          IllegalStateException.class,
          () -> application.bind("typed", Point.class, (_, point) -> point));
      Assertions.assertThrows(
          IllegalStateException.class, () -> application.emit("tick", new Point(1, 2)));
      Assertions.assertThrows(
          IllegalStateException.class, () -> application.listen("tick", Point.class, _ -> {}));
      Assertions.assertDoesNotThrow(() -> application.emit("tick", "text"));
    }
  }

  @Test
  void applicationListenersHearEveryWindowAndKnowWhich() throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().codec(new PointCodec()).build();
    try (FakeApplication application = new FakeApplication(parameters)) {
      BlockingQueue<Event> heard = new LinkedBlockingQueue<>();
      BlockingQueue<Point> points = new LinkedBlockingQueue<>();
      application.listen("note", heard::add);
      application.listen("moved", Point.class, points::add);
      final FakeWindow one = application.openFake();
      final FakeWindow two = application.openFake();

      // From a page.
      two.receive("1" + SEP + BridgeProtocol.EVENT_CALL + SEP + "0" + SEP + "note" + SEP + "hi");
      Event fromPage = heard.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(fromPage);
      Assertions.assertEquals("hi", fromPage.payload());
      Assertions.assertSame(two, fromPage.window());

      // From a window's own emit.
      one.emit("note", "java");
      Event fromWindow = heard.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(fromWindow);
      Assertions.assertEquals("java", fromWindow.payload());
      Assertions.assertSame(one, fromWindow.window());
      Assertions.assertTrue(fromWindow.id() > fromPage.id(), "IDs count deliveries");

      one.receive("2" + SEP + BridgeProtocol.EVENT_CALL + SEP + "1" + SEP + "moved" + SEP + "3,4");
      Assertions.assertEquals(new Point(3, 4), points.poll(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void applicationEmitReachesEveryPageAndEveryListener() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      BlockingQueue<Event> atApplication = new LinkedBlockingQueue<>();
      BlockingQueue<Event> atWindow = new LinkedBlockingQueue<>();
      application.listen("tick", atApplication::add);
      final FakeWindow one = application.openFake();
      final FakeWindow two = application.openFake();
      one.listen("tick", atWindow::add);

      application.emit("tick", "all");

      String script = BridgeProtocol.emitScript("tick", "all", false);
      Assertions.assertTrue(one.evaluated.contains(script));
      Assertions.assertTrue(two.evaluated.contains(script));
      Event broadcast = atApplication.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(broadcast);
      Assertions.assertEquals("all", broadcast.payload());
      Assertions.assertNull(broadcast.window(), "a broadcast comes from no window");
      Assertions.assertNull(atApplication.poll(200, TimeUnit.MILLISECONDS), "heard once");
      Event local = atWindow.poll(5, TimeUnit.SECONDS);
      Assertions.assertNotNull(local);
      Assertions.assertSame(one, local.window());
    }
  }

  @Test
  void onceAtTheApplicationHearsOneEvent() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      BlockingQueue<String> once = new LinkedBlockingQueue<>();
      application.once("tick", event -> once.add(event.payload()));
      application.emit("tick", "a");
      application.emit("tick", "b");
      Assertions.assertEquals("a", once.poll(5, TimeUnit.SECONDS));
      Assertions.assertNull(once.poll(200, TimeUnit.MILLISECONDS));
    }
  }

  /**
   * Suppressed warnings: {@code resource}: the window that the page opened looks like an unclosed
   * resource. The application owns it and closes it when the test closes the application.
   */
  @SuppressWarnings("resource")
  @Test
  void pageOpensWindowThroughTheReservedCall() throws Exception {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow opener = application.openFake();
      String options =
          String.join(SEP, "child", "320", "240", "10", "20", "1", "", "app/child.html");
      opener.receive("7" + SEP + BridgeProtocol.OPEN_CALL + SEP + options);

      opener.awaitEvaluation(BridgeProtocol.resolveScript(7, "2"));
      FakeWindow child = (FakeWindow) application.window(2).orElseThrow();
      Assertions.assertEquals("child", child.title());
      Assertions.assertEquals(320, child.width());
      Assertions.assertEquals(240, child.height());
      Assertions.assertTrue(child.isShown(), "a window opened from a page shows itself");
      Assertions.assertEquals(List.of("app://local/app/child.html"), child.navigated);

      opener.receive("8" + SEP + BridgeProtocol.OPEN_CALL + SEP + "garbage");
      opener.awaitEvaluation(BridgeProtocol.rejectScript(8, "Malformed window parameters"));
    }
  }

  @Test
  void pageClosesItsWindowThroughTheReservedCall() {
    try (FakeApplication application = new FakeApplication()) {
      FakeWindow window = application.openFake();
      window.receive("1" + SEP + BridgeProtocol.CLOSE_CALL + SEP);
      // It's the only window, so run() returns once the page closed it.
      application.run();
      Assertions.assertTrue(window.isClosed());
      Assertions.assertTrue(application.windows().isEmpty());
    }
  }
}
