package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.Point;
import dev.ivchenko.lwjwae.testing.Screenshots;
import dev.ivchenko.lwjwae.testing.Tags;
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

      Loads.eval(
          backend,
          """
          window.__heard = [];
          window.lwjwae.on('tick', payload => window.__heard.push(payload));
          window.addEventListener('lwjwae:tick', event => window.__heard.push('dom:' + event.detail));
          undefined;\
          """);
      backend.emit("tick", "one");
      Assertions.assertEquals(
          "one,dom:one",
          Loads.awaitValue(
              backend, "window.__heard.length === 2 ? window.__heard.join() : undefined"));

      Loads.eval(
          backend,
          "window.lwjwae.on('point', p => { window.__point = p.x * 10 + p.y; }); undefined;");
      backend.emit("point", new Point(4, 2));
      Assertions.assertEquals("42", Loads.awaitValue(backend, "window.__point"));

      Loads.eval(
          backend,
          """
          window.__count = 0;
          const listener = () => window.__count++;
          window.lwjwae.on('once', listener);
          window.lwjwae.off('once', listener);
          undefined;\
          """);
      backend.emit("once", "ignored");
      backend.emit("point", new Point(0, 0));
      Assertions.assertEquals(
          "settled", Loads.awaitValue(backend, "window.__point === 0 ? 'settled' : undefined"));
      Assertions.assertEquals("0", Loads.eval(backend, "String(window.__count)"));
    }
  }
}
