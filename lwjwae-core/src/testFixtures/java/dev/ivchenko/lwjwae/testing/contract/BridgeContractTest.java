package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.Point;
import dev.ivchenko.lwjwae.testing.Screenshots;
import dev.ivchenko.lwjwae.testing.Tags;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests serving the files of the application, and calling Java from the page. */
@Tag(Tags.DISPLAY)
@Timeout(90)
public abstract class BridgeContractTest extends DisplayContractTest {
  @Test
  void servesClasspathResourcesAndCallsBackIntoJava() throws Exception {
    WindowParameters parameters = WindowParameters.builder().title("bridge").build();
    try (Application application = Application.create()) {
      Window window = application.open(parameters);
      final var loaded = Loads.expectFinished(window);
      window.bind("reverse", payload -> new StringBuilder(payload).reverse().toString());
      window.bind(
          "boom",
          _ -> {
            throw new IllegalStateException("handler exploded");
          });

      window.show();
      window.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      Assertions.assertEquals(
          "bridge test", Loads.eval(window, "document.querySelector('h1').textContent"));
      Screenshots.capture("bridge-classpath-app");
      Assertions.assertEquals("true", Loads.eval(window, "String(window.__scriptLoaded)"));
      Assertions.assertEquals(
          "rgb(17, 34, 51)",
          Loads.eval(window, "getComputedStyle(document.querySelector('h1')).color"));

      // Trailing `undefined` because eval cannot hand a Promise back to Java.
      Loads.eval(
          window,
          "window.reverse('lwjwae').then(value => { window.__reversed = value; }); undefined;");
      Assertions.assertEquals("eawjwl", Loads.awaitValue(window, "window.__reversed"));

      Loads.eval(
          window,
          "window.boom('x').catch(error => { window.__failure = error.message; }); undefined;");
      Assertions.assertEquals("handler exploded", Loads.awaitValue(window, "window.__failure"));
    }
  }

  @Test
  void bindingAfterThePageLoadedStillWorks() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      window.bind("late", payload -> "late:" + payload);
      Loads.eval(window, "window.late('x').then(value => { window.__late = value; }); undefined;");
      Assertions.assertEquals("late:x", Loads.awaitValue(window, "window.__late"));
    }
  }

  @Test
  void payloadsSurviveTheRoundTripIntact() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      final var loaded = Loads.expectFinished(window);
      window.bind("echo", Function.identity());
      window.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      // Quotes, backslashes, newlines and non-Latin text: none may break either side's quoting.
      Loads.eval(
          window,
          """
          window.echo('a "b" \\\\ c\\nпривет 世界').then(value => { window.__echo = value; }); undefined;\
          """);
      Assertions.assertEquals("a \"b\" \\ c\nпривет 世界", Loads.awaitValue(window, "window.__echo"));

      Loads.eval(
          window,
          "window.echo({ n: 1, s: 'x' }).then(value => { window.__json = value; }); undefined;");
      Assertions.assertEquals("{\"n\":1,\"s\":\"x\"}", Loads.awaitValue(window, "window.__json"));
    }
  }

  @Test
  void typedBindingsGoThroughTheCodec() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      final var loaded = Loads.expectFinished(window);
      window.bind("mirror", Point.class, point -> new Point(point.y(), point.x()));
      window.bind("origin", Void.class, _ -> new Point(0, 0));
      window.bind("nothing", Point.class, _ -> null);
      window.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      Loads.eval(
          window,
          "window.mirror({ x: 1, y: 2 }).then(p => { window.__mirror = p.x + ':' + p.y; });"
              + " undefined;");
      Assertions.assertEquals("2:1", Loads.awaitValue(window, "window.__mirror"));
      Loads.eval(
          window,
          "window.origin().then(p => { window.__origin = JSON.stringify(p); }); undefined;");
      Assertions.assertEquals("{\"x\":0,\"y\":0}", Loads.awaitValue(window, "window.__origin"));
      Loads.eval(
          window,
          "window.nothing({ x: 1, y: 1 }).then(v => { window.__nothing = String(v); });"
              + " undefined;");
      Assertions.assertEquals("null", Loads.awaitValue(window, "window.__nothing"));
    }
  }

  @Test
  void windowBindingKeepsItsPageFormOverTypedApplicationBinding() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      final var loaded = Loads.expectFinished(window);
      window.bind("shout", text -> text.toUpperCase() + "!");
      // Typed, and bound later: its page script must not replace the window's untyped one, or the
      // page would send "\"plain\"" and fail to decode the answer.
      application.bind("shout", Point.class, Function.identity());
      window.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      Loads.eval(
          window,
          "window.shout('plain').then(v => { window.__shout = v; },"
              + " e => { window.__shout = 'rejected: ' + e.message; }); undefined;");
      Assertions.assertEquals("PLAIN!", Loads.awaitValue(window, "window.__shout"));
    }
  }

  @Test
  void eventsReachTheListenersOfThePage() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      // A listener gets { event, id, payload }, like Tauri's.
      Loads.eval(
          window,
          """
          window.__heard = [];
          window.lwjwae.listen('tick', e => window.__heard.push(e.event + ':' + e.payload));
          undefined;\
          """);
      window.emit("tick", "one");
      Assertions.assertEquals(
          "tick:one",
          Loads.awaitValue(
              window, "window.__heard.length === 1 ? window.__heard.join() : undefined"));

      // A typed payload arrives decoded.
      Loads.eval(
          window,
          "window.lwjwae.listen('point', e => { window.__point = e.payload.x * 10 + e.payload.y;"
              + " }); undefined;");
      window.emit("point", new Point(4, 2));
      Assertions.assertEquals("42", Loads.awaitValue(window, "window.__point"));

      // once hears one event; the unlisten function that listen resolves to stops the rest.
      Loads.eval(
          window,
          """
          window.__once = 0;
          window.__count = 0;
          window.lwjwae.once('once', () => window.__once++);
          window.lwjwae.listen('once', () => window.__count++).then(unlisten => {
            window.__unlisten = unlisten;
          });
          undefined;\
          """);
      Loads.awaitValue(window, "typeof window.__unlisten === 'function' ? 'ready' : undefined");
      window.emit("once", "first");
      window.emit("once", "second");
      Assertions.assertEquals(
          "2", Loads.awaitValue(window, "window.__count === 2 ? '2' : undefined"));
      Assertions.assertEquals("1", Loads.eval(window, "String(window.__once)"));
      Loads.eval(window, "window.__unlisten(); undefined;");
      window.emit("once", "third");
      window.emit("point", new Point(0, 0));
      Assertions.assertEquals(
          "settled", Loads.awaitValue(window, "window.__point === 0 ? 'settled' : undefined"));
      Assertions.assertEquals("2", Loads.eval(window, "String(window.__count)"));
    }
  }

  @Test
  void eventsFromThePageReachJavaAndThePage() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      final var loaded = Loads.expectFinished(window);
      window.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      BlockingQueue<Event> raw = new LinkedBlockingQueue<>();
      BlockingQueue<Point> points = new LinkedBlockingQueue<>();
      window.listen("note", raw::add);
      window.listen("moved", Point.class, points::add);

      // A string goes as it is; an object goes through the codec. The promise resolves after Java
      // took the event, and the page's own listeners hear it too.
      Loads.eval(
          window,
          """
          window.__local = [];
          window.lwjwae.listen('note', e => window.__local.push(e.payload));
          window.lwjwae.emit('note', 'from the page').then(() => { window.__sent = 'yes'; });
          window.lwjwae.emit('moved', { x: 7, y: 9 });
          undefined;\
          """);
      Event note = raw.poll(10, TimeUnit.SECONDS);
      Assertions.assertNotNull(note, "Java must hear the event of the page");
      Assertions.assertEquals("from the page", note.payload());
      Assertions.assertFalse(note.typed());
      Assertions.assertEquals(new Point(7, 9), points.poll(10, TimeUnit.SECONDS));
      Assertions.assertEquals("yes", Loads.awaitValue(window, "window.__sent"));
      Assertions.assertEquals("from the page", Loads.eval(window, "window.__local.join()"));
    }
  }
}
