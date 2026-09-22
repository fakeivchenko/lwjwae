package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationParameters;
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
    ApplicationParameters parameters = ApplicationParameters.builder().title("bridge").build();
    try (ApplicationBackend backend = Application.create(parameters)) {
      final var loaded = Loads.expectFinished(backend);
      backend.bind("reverse", payload -> new StringBuilder(payload).reverse().toString());
      backend.bind(
          "boom",
          _ -> {
            throw new IllegalStateException("handler exploded");
          });

      backend.show();
      backend.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      Assertions.assertEquals(
          "bridge test", Loads.eval(backend, "document.querySelector('h1').textContent"));
      Screenshots.capture("bridge-classpath-app");
      Assertions.assertEquals("true", Loads.eval(backend, "String(window.__scriptLoaded)"));
      Assertions.assertEquals(
          "rgb(17, 34, 51)",
          Loads.eval(backend, "getComputedStyle(document.querySelector('h1')).color"));

      // Trailing `undefined` because eval cannot hand a Promise back to Java.
      Loads.eval(
          backend,
          "window.reverse('lwjwae').then(value => { window.__reversed = value; }); undefined;");
      Assertions.assertEquals("eawjwl", Loads.awaitValue(backend, "window.__reversed"));

      Loads.eval(
          backend,
          "window.boom('x').catch(error => { window.__failure = error.message; }); undefined;");
      Assertions.assertEquals("handler exploded", Loads.awaitValue(backend, "window.__failure"));
    }
  }

  @Test
  void bindingAfterThePageLoadedStillWorks() throws Exception {
    try (ApplicationBackend backend = Application.create()) {
      final var loaded = Loads.expectFinished(backend);
      backend.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      backend.bind("late", payload -> "late:" + payload);
      Loads.eval(backend, "window.late('x').then(value => { window.__late = value; }); undefined;");
      Assertions.assertEquals("late:x", Loads.awaitValue(backend, "window.__late"));
    }
  }

  @Test
  void payloadsSurviveTheRoundTripIntact() throws Exception {
    try (ApplicationBackend backend = Application.create()) {
      final var loaded = Loads.expectFinished(backend);
      backend.bind("echo", Function.identity());
      backend.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      // Quotes, backslashes, newlines and non-Latin text: none may break either side's quoting.
      Loads.eval(
          backend,
          """
          window.echo('a "b" \\\\ c\\nпривет 世界').then(value => { window.__echo = value; }); undefined;\
          """);
      Assertions.assertEquals(
          "a \"b\" \\ c\nпривет 世界", Loads.awaitValue(backend, "window.__echo"));

      Loads.eval(
          backend,
          "window.echo({ n: 1, s: 'x' }).then(value => { window.__json = value; }); undefined;");
      Assertions.assertEquals("{\"n\":1,\"s\":\"x\"}", Loads.awaitValue(backend, "window.__json"));
    }
  }

  @Test
  void typedBindingsGoThroughTheCodec() throws Exception {
    try (ApplicationBackend backend = Application.create()) {
      final var loaded = Loads.expectFinished(backend);
      backend.bind("mirror", Point.class, point -> new Point(point.y(), point.x()));
      backend.bind("origin", Void.class, _ -> new Point(0, 0));
      backend.bind("nothing", Point.class, _ -> null);
      backend.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      Loads.eval(
          backend,
          "window.mirror({ x: 1, y: 2 }).then(p => { window.__mirror = p.x + ':' + p.y; });"
              + " undefined;");
      Assertions.assertEquals("2:1", Loads.awaitValue(backend, "window.__mirror"));
      Loads.eval(
          backend,
          "window.origin().then(p => { window.__origin = JSON.stringify(p); }); undefined;");
      Assertions.assertEquals("{\"x\":0,\"y\":0}", Loads.awaitValue(backend, "window.__origin"));
      Loads.eval(
          backend,
          "window.nothing({ x: 1, y: 1 }).then(v => { window.__nothing = String(v); });"
              + " undefined;");
      Assertions.assertEquals("null", Loads.awaitValue(backend, "window.__nothing"));
    }
  }

  @Test
  void eventsReachTheListenersOfThePage() throws Exception {
    try (ApplicationBackend backend = Application.create()) {
      final var loaded = Loads.expectFinished(backend);
      backend.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      // A listener gets { event, id, payload }, like Tauri's.
      Loads.eval(
          backend,
          """
          window.__heard = [];
          window.lwjwae.listen('tick', e => window.__heard.push(e.event + ':' + e.payload));
          undefined;\
          """);
      backend.emit("tick", "one");
      Assertions.assertEquals(
          "tick:one",
          Loads.awaitValue(
              backend, "window.__heard.length === 1 ? window.__heard.join() : undefined"));

      // A typed payload arrives decoded.
      Loads.eval(
          backend,
          "window.lwjwae.listen('point', e => { window.__point = e.payload.x * 10 + e.payload.y;"
              + " }); undefined;");
      backend.emit("point", new Point(4, 2));
      Assertions.assertEquals("42", Loads.awaitValue(backend, "window.__point"));

      // once hears one event; the unlisten function that listen resolves to stops the rest.
      Loads.eval(
          backend,
          """
          window.__once = 0;
          window.__count = 0;
          window.lwjwae.once('once', () => window.__once++);
          window.lwjwae.listen('once', () => window.__count++).then(unlisten => {
            window.__unlisten = unlisten;
          });
          undefined;\
          """);
      Loads.awaitValue(backend, "typeof window.__unlisten === 'function' ? 'ready' : undefined");
      backend.emit("once", "first");
      backend.emit("once", "second");
      Assertions.assertEquals(
          "2", Loads.awaitValue(backend, "window.__count === 2 ? '2' : undefined"));
      Assertions.assertEquals("1", Loads.eval(backend, "String(window.__once)"));
      Loads.eval(backend, "window.__unlisten(); undefined;");
      backend.emit("once", "third");
      backend.emit("point", new Point(0, 0));
      Assertions.assertEquals(
          "settled", Loads.awaitValue(backend, "window.__point === 0 ? 'settled' : undefined"));
      Assertions.assertEquals("2", Loads.eval(backend, "String(window.__count)"));
    }
  }

  @Test
  void eventsFromThePageReachJavaAndThePage() throws Exception {
    try (ApplicationBackend backend = Application.create()) {
      final var loaded = Loads.expectFinished(backend);
      backend.loadResource("test-app/index.html");
      loaded.get(60, TimeUnit.SECONDS);

      BlockingQueue<Event> raw = new LinkedBlockingQueue<>();
      BlockingQueue<Point> points = new LinkedBlockingQueue<>();
      backend.listen("note", raw::add);
      backend.listen("moved", Point.class, points::add);

      // A string goes as it is; an object goes through the codec. The promise resolves after Java
      // took the event, and the page's own listeners hear it too.
      Loads.eval(
          backend,
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
      Assertions.assertEquals("yes", Loads.awaitValue(backend, "window.__sent"));
      Assertions.assertEquals("from the page", Loads.eval(backend, "window.__local.join()"));
    }
  }
}
