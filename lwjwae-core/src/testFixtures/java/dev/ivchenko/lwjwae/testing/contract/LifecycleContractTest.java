package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.Tags;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
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

    try (Application application = Application.create()) {
      Window window = application.open();
      var failed = Loads.expectFailed(window);
      window.navigate(url);

      LoadEvent event = failed.get(30, TimeUnit.SECONDS);
      Assertions.assertEquals(url, event.url());
      Assertions.assertNotNull(event.message());
    }
  }

  @Test
  void missingResourceReportsFailedLoad() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      var failed = Loads.expectFailed(window);
      window.loadResource("test-app/does-not-exist.html");

      LoadEvent event = failed.get(30, TimeUnit.SECONDS);
      Assertions.assertTrue(
          event.message().contains("test-app/does-not-exist.html"), event.message());
    }
  }

  @Test
  void throwingScriptFailsTheFuture() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      final var loaded = Loads.expectFinished(window);
      window.html("<html><body></body></html>");
      loaded.get(30, TimeUnit.SECONDS);

      ExecutionException failure =
          Assertions.assertThrows(
              ExecutionException.class,
              () -> window.eval("throw new Error('kaboom')").get(20, TimeUnit.SECONDS));
      Assertions.assertInstanceOf(ScriptEvaluationFailedException.class, failure.getCause());
    }
  }

  @Test
  void loadListenerOnTheUiThreadOpensWindow() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      CompletableFuture<Window> opened = new CompletableFuture<>();
      window.onLoad(
          event -> {
            if (event.state() != LoadState.FINISHED || opened.isDone()) {
              return;
            }
            try {
              opened.complete(application.open(WindowParameters.builder().title("Second").build()));
            } catch (Throwable t) {
              opened.completeExceptionally(t);
            }
          });
      window.html("<html><body></body></html>");

      Window second = opened.get(30, TimeUnit.SECONDS);
      Assertions.assertEquals("Second", second.title());
      Assertions.assertEquals(List.of(window, second), application.windows());

      final var loaded = Loads.expectFinished(second);
      second.html("<html><body></body></html>");
      loaded.get(30, TimeUnit.SECONDS);
      Assertions.assertEquals("2", second.eval("1 + 1").get(20, TimeUnit.SECONDS));
    }
  }

  @Test
  void closedWindowRejectsEveryCall() {
    try (Application application = Application.create()) {
      Window window = application.open();
      window.show();
      window.close();

      Assertions.assertThrows(IllegalStateException.class, window::title);
      Assertions.assertThrows(IllegalStateException.class, () -> window.navigate("about:blank"));
      ExecutionException failure =
          Assertions.assertThrows(
              ExecutionException.class, () -> window.eval("1").get(20, TimeUnit.SECONDS));
      Assertions.assertInstanceOf(IllegalStateException.class, failure.getCause());

      Assertions.assertDoesNotThrow(window::close, "close() must be idempotent");
    }
  }

  @Test
  void closingFromAnotherThreadUnblocksRun() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open(WindowParameters.builder().title("run").build());
      window.show();
      Thread runner = new Thread(application::run, "run-caller");
      runner.start();

      Thread.sleep(500);
      Assertions.assertTrue(runner.isAlive(), "run() must block while the window is open");
      window.close();
      runner.join(10_000);
      Assertions.assertFalse(runner.isAlive(), "run() must return once the window is closed");
    }
  }

  @Test
  void twoWindowsAreIndependent() throws Exception {
    WindowParameters first = WindowParameters.builder().title("first").build();
    WindowParameters second = WindowParameters.builder().title("second").build();
    try (Application application = Application.create()) {
      Window one = application.open(first);
      Window two = application.open(second);
      Assertions.assertEquals(List.of(one, two), application.windows());
      Assertions.assertNotEquals(one.id(), two.id());
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
      Assertions.assertEquals(List.of(two), application.windows());
      Assertions.assertTrue(application.window(one.id()).isEmpty());
    }
  }

  @Test
  void runReturnsWhenTheLastWindowClosesAndQuitClosesEveryWindow() throws Exception {
    try (Application application = Application.create()) {
      Assertions.assertDoesNotThrow(application::run, "run() with no window must return at once");

      final Window first = application.open(WindowParameters.builder().title("first").build());
      Thread runner = new Thread(application::run, "run-caller");
      runner.start();
      Thread.sleep(500);
      Assertions.assertTrue(runner.isAlive(), "run() must block while a window is open");

      final Window second = application.open(WindowParameters.builder().title("second").build());
      first.close();
      Thread.sleep(500);
      Assertions.assertTrue(runner.isAlive(), "run() must block while any window is open");

      second.close();
      runner.join(10_000);
      Assertions.assertFalse(runner.isAlive(), "run() must return once the last window closed");
      Assertions.assertFalse(application.isClosed(), "a closed window doesn't quit");

      Window third = application.open();
      application.quit();
      Assertions.assertTrue(third.isClosed(), "quit() must close every window");
      Assertions.assertTrue(application.isClosed());
      Assertions.assertTrue(application.windows().isEmpty());
      Assertions.assertThrows(IllegalStateException.class, application::open);
      Assertions.assertDoesNotThrow(application::quit, "quit() must be idempotent");
    }
  }

  /**
   * Suppressed warnings: {@code resource}: {@link dev.ivchenko.lwjwae.event.Event#window()} looks
   * like an unclosed resource. The listener only reads its ID; the application owns the window.
   */
  @SuppressWarnings("resource")
  @Test
  void applicationBindingsAndListenersSpanEveryWindow() throws Exception {
    try (Application application = Application.create()) {
      BlockingQueue<String> heard = new LinkedBlockingQueue<>();
      application.listen("note", event -> heard.add(event.window().id() + ":" + event.payload()));
      application.bind("whoami", (window, _) -> "window " + window.id());

      Window one = application.open();
      final var oneLoaded = Loads.expectFinished(one);
      one.loadResource("test-app/index.html");
      oneLoaded.get(60, TimeUnit.SECONDS);
      Window two = application.open();
      final var twoLoaded = Loads.expectFinished(two);
      two.loadResource("test-app/index.html");
      twoLoaded.get(60, TimeUnit.SECONDS);

      // Bound before either window existed, the function is in both documents.
      Loads.eval(one, "window.whoami().then(v => { window.__who = v; }); undefined;");
      Loads.eval(two, "window.whoami().then(v => { window.__who = v; }); undefined;");
      Assertions.assertEquals("window " + one.id(), Loads.awaitValue(one, "window.__who"));
      Assertions.assertEquals("window " + two.id(), Loads.awaitValue(two, "window.__who"));

      // Bound while both are open, the function reaches the documents already on screen.
      application.bind("late", payload -> "late:" + payload);
      Loads.eval(two, "window.late('x').then(v => { window.__late = v; }); undefined;");
      Assertions.assertEquals("late:x", Loads.awaitValue(two, "window.__late"));

      // A window-level binding of the same name wins in that window only.
      one.bind("whoami", _ -> "mine");
      Loads.eval(one, "window.whoami().then(v => { window.__who2 = v; }); undefined;");
      Loads.eval(two, "window.whoami().then(v => { window.__who2 = v; }); undefined;");
      Assertions.assertEquals("mine", Loads.awaitValue(one, "window.__who2"));
      Assertions.assertEquals("window " + two.id(), Loads.awaitValue(two, "window.__who2"));

      // The application hears the events of every page, and knows which page.
      Loads.eval(one, "window.lwjwae.emit('note', 'from one'); undefined;");
      Assertions.assertEquals(one.id() + ":from one", heard.poll(10, TimeUnit.SECONDS));
      Loads.eval(two, "window.lwjwae.emit('note', 'from two'); undefined;");
      Assertions.assertEquals(two.id() + ":from two", heard.poll(10, TimeUnit.SECONDS));

      // An application-level emit reaches every page.
      Loads.eval(
          one, "window.lwjwae.listen('tick', e => { window.__tick = e.payload; }); undefined;");
      Loads.eval(
          two, "window.lwjwae.listen('tick', e => { window.__tick = e.payload; }); undefined;");
      application.emit("tick", "all");
      Assertions.assertEquals("all", Loads.awaitValue(one, "window.__tick"));
      Assertions.assertEquals("all", Loads.awaitValue(two, "window.__tick"));
    }
  }

  @Test
  void pageOpensAndClosesWindows() throws Exception {
    try (Application application = Application.create()) {
      Window first = application.open();
      final var loaded = Loads.expectFinished(first);
      first.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      Loads.eval(
          first,
          "window.lwjwae.open({ title: 'opened', width: 320, height: 240,"
              + " resource: 'test-app/index.html' }).then(id => { window.__opened = id; });"
              + " undefined;");
      long id = Long.parseLong(Loads.awaitValue(first, "window.__opened"));
      Window second = application.window(id).orElseThrow();
      Assertions.assertEquals("opened", second.title());
      Assertions.assertEquals(List.of(first, second), application.windows());
      Assertions.assertEquals(
          "bridge test", Loads.awaitValue(second, "document.querySelector('h1')?.textContent"));

      Loads.eval(second, "window.lwjwae.close(); undefined;");
      Loads.awaitClosed(second);
      Assertions.assertEquals(List.of(first), application.windows());
    }
  }
}
