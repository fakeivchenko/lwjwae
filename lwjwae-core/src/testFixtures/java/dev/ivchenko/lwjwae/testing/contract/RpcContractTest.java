package dev.ivchenko.lwjwae.testing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.event.LoadState;
import dev.ivchenko.lwjwae.rpc.RpcException;
import dev.ivchenko.lwjwae.rpc.RpcStream;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.Point;
import dev.ivchenko.lwjwae.testing.Tags;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Calls Java from a real page through {@code lwjwae.call} and {@code lwjwae.invoke}, over whatever
 * transport the backend picked.
 *
 * <p>Each test registers handlers, loads a blank page, and evaluates a script that makes the calls
 * and reports what it saw through a binding, so the assertions read one JSON object.
 */
@Tag(Tags.DISPLAY)
@Timeout(120)
public abstract class RpcContractTest extends DisplayContractTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void bytesGoBothWaysUnchanged() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      window.handle("echo", call -> call.reply(call.body(), "application/octet-stream"));
      JsonNode result =
          run(
              window,
              """
              const data = new Uint8Array(262144);
              for (let i = 0; i < data.length; i++) data[i] = (i * 31 + 7) & 255;
              const response = await lwjwae.call("echo", data);
              const back = new Uint8Array(await response.arrayBuffer());
              let same = back.length === data.length;
              for (let i = 0; same && i < data.length; i++) if (back[i] !== data[i]) same = false;
              return { status: response.status, type: response.headers.get("Content-Type"), same };
              """);
      Assertions.assertTrue(result.path("same").asBoolean(), result.toString());
      Assertions.assertEquals(200, result.path("status").asInt());
      Assertions.assertEquals("application/octet-stream", result.path("type").asText());
    }
  }

  @Test
  void textAndValuesGoThroughTheCodec() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      window.handle("greet", call -> call.reply("Hello, " + call.text()));
      window.handle(
          "mirror",
          call -> {
            Point point = call.value(Point.class);
            call.replyValue(new Point(point.y(), point.x()));
          });
      window.handle("nothing", _ -> {});
      JsonNode result =
          run(
              window,
              """
              const greeting = await (await lwjwae.call("greet", "Ann")).text();
              const mirrored = await lwjwae.invoke("mirror", { x: 1, y: 2 });
              const empty = await lwjwae.call("nothing");
              return { greeting, mirrored, emptyStatus: empty.status, emptyText: await empty.text(),
                       invokeNothing: String(await lwjwae.invoke("nothing")) };
              """);
      Assertions.assertEquals("Hello, Ann", result.path("greeting").asText(), result.toString());
      Assertions.assertEquals(2, result.path("mirrored").path("x").asInt(), result.toString());
      Assertions.assertEquals(1, result.path("mirrored").path("y").asInt(), result.toString());
      Assertions.assertEquals(204, result.path("emptyStatus").asInt(), result.toString());
      Assertions.assertEquals("", result.path("emptyText").asText());
      Assertions.assertEquals("undefined", result.path("invokeNothing").asText());
    }
  }

  @Test
  void streamArrivesInPartsWhileItIsWritten() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      window.handle(
          "count",
          call -> {
            try (RpcStream stream = call.stream("text/plain")) {
              for (int part = 0; part < 5; part++) {
                if (part > 0) {
                  Thread.sleep(300);
                }
                stream.write(("part " + part + "\n").getBytes(StandardCharsets.UTF_8));
              }
            }
          });
      JsonNode result =
          run(
              window,
              """
              const start = performance.now();
              const response = await lwjwae.call("count");
              const reader = response.body.getReader();
              const times = [];
              let text = "";
              for (;;) {
                const { done, value } = await reader.read();
                if (done) break;
                times.push(Math.round(performance.now() - start));
                text += new TextDecoder().decode(value);
              }
              return { text, first: times[0], last: times[times.length - 1] };
              """);
      Assertions.assertTrue(result.path("text").asText().contains("part 4"), result.toString());
      Assertions.assertTrue(
          result.path("last").asInt() - result.path("first").asInt() >= 600,
          "the parts must arrive over time, not at once: " + result);
    }
  }

  @Test
  void abortReachesTheHandler() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      CompletableFuture<String> handlerSaw = new CompletableFuture<>();
      window.handle(
          "hold",
          call -> {
            try (RpcStream stream = call.stream("text/plain")) {
              long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
              while (System.nanoTime() < deadline) {
                if (!stream.write("tick\n".getBytes(StandardCharsets.UTF_8))) {
                  handlerSaw.complete("write refused");
                  return;
                }
                Thread.sleep(50);
              }
              handlerSaw.complete("never cancelled");
            } catch (InterruptedException e) {
              handlerSaw.complete("interrupted, cancelled=" + call.isCancelled());
            }
          });
      JsonNode result =
          run(
              window,
              """
              const controller = new AbortController();
              setTimeout(() => controller.abort(), 500);
              try {
                const response = await lwjwae.call("hold", null, { signal: controller.signal });
                const reader = response.body.getReader();
                for (;;) { const { done } = await reader.read(); if (done) break; }
                return { outcome: "finished" };
              } catch (error) {
                return { outcome: error.name };
              }
              """);
      Assertions.assertEquals("AbortError", result.path("outcome").asText(), result.toString());
      String saw = handlerSaw.get(10, TimeUnit.SECONDS);
      Assertions.assertNotEquals("never cancelled", saw);
    }
  }

  @Test
  void failuresCarryStatusAndCode() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      window.handle(
          "refuse",
          _ -> {
            throw RpcException.badRequest("bad-input", "Not like this");
          });
      window.handle(
          "crash",
          _ -> {
            throw new IllegalStateException("Boom");
          });
      JsonNode result =
          run(
              window,
              """
              const outcome = async (name) => {
                try { await lwjwae.call(name); return { ok: true }; }
                catch (error) { return { name: error.name, status: error.status, code: error.code, message: error.message }; }
              };
              return { refuse: await outcome("refuse"), crash: await outcome("crash"),
                       missing: await outcome("no.such.name") };
              """);
      JsonNode refuse = result.path("refuse");
      Assertions.assertEquals(400, refuse.path("status").asInt(), result.toString());
      Assertions.assertEquals("bad-input", refuse.path("code").asText());
      Assertions.assertEquals("Not like this", refuse.path("message").asText());
      Assertions.assertEquals("RpcError", refuse.path("name").asText());
      Assertions.assertEquals(500, result.path("crash").path("status").asInt(), result.toString());
      Assertions.assertEquals("Boom", result.path("crash").path("message").asText());
      Assertions.assertEquals(
          404, result.path("missing").path("status").asInt(), result.toString());
      Assertions.assertEquals("not-found", result.path("missing").path("code").asText());
    }
  }

  @Test
  void windowHandlerWinsOverTheApplicationOne() throws Exception {
    try (Application application = Application.create()) {
      application.handle("who", call -> call.reply("application"));
      Window own = application.open();
      own.handle("who", call -> call.reply("window"));
      Window other = application.open();
      String script = "return { who: await (await lwjwae.call(\"who\")).text() };";
      Assertions.assertEquals("window", run(own, script).path("who").asText());
      Assertions.assertEquals("application", run(other, script).path("who").asText());
    }
  }

  @Test
  void developmentServerMayCallAndOtherOriginsMayNot() throws Exception {
    HttpServer development = pageServer();
    HttpServer stranger = pageServer();
    String developmentUrl = "http://127.0.0.1:" + development.getAddress().getPort();
    ApplicationParameters parameters =
        ApplicationParameters.builder().devServerUrl(developmentUrl).build();
    try (Application application = Application.create(parameters)) {
      application.handle("ping", call -> call.reply("pong"));
      String script =
          """
          try { return { answer: await (await lwjwae.call("ping")).text(), origin: location.origin }; }
          catch (error) { return { status: error.status, code: error.code, origin: location.origin }; }
          """;
      JsonNode allowed = run(application.open(), script, developmentUrl + "/rpc/blank.html");
      Assertions.assertEquals("pong", allowed.path("answer").asText(), allowed.toString());
      JsonNode refused =
          run(
              application.open(),
              script,
              "http://127.0.0.1:" + stranger.getAddress().getPort() + "/rpc/blank.html");
      Assertions.assertEquals(403, refused.path("status").asInt(), refused.toString());
    } finally {
      development.stop(0);
      stranger.stop(0);
    }
  }

  @Test
  void benchmarkAgainstTheBridge() throws Exception {
    try (Application application = Application.create()) {
      Window window = application.open();
      window.bind("bridgePing", payload -> payload);
      window.listen("ping", _ -> window.emit("pong", "x"));
      window.listen(
          "burst-please",
          _ -> {
            for (int i = 0; i < 10000; i++) {
              window.emit("burst", "event " + i);
            }
          });
      window.handle("ping", call -> call.reply(call.body(), "text/plain"));
      byte[] part = new byte[262144];
      window.handle(
          "bulk",
          call -> {
            long total = 32L * 1024 * 1024;
            try (RpcStream stream = call.stream("application/octet-stream")) {
              for (long sent = 0; sent < total; sent += part.length) {
                if (!stream.write(part)) {
                  return;
                }
              }
            }
          });
      JsonNode result =
          run(
              window,
              """
              const time = async (count, call) => {
                const start = performance.now();
                for (let i = 0; i < count; i++) await call();
                return +((performance.now() - start) / count).toFixed(3);
              };
              const bridgeCallMs = await time(500, () => bridgePing("x"));
              const rpcCallMs = await time(500, async () => (await lwjwae.call("ping", "x")).text());
              const start = performance.now();
              const reader = (await lwjwae.call("bulk")).body.getReader();
              let bytes = 0;
              for (;;) { const { done, value } = await reader.read(); if (done) break; bytes += value.length; }
              const rpcStreamMBps = +((bytes / 1048576) / ((performance.now() - start) / 1000)).toFixed(1);
              const eventRoundTripMs = await time(500, () => new Promise((resolve) => {
                lwjwae.once("pong", resolve);
                lwjwae.emit("ping", "x");
              }));
              let heard = 0;
              const burst = new Promise((resolve) => lwjwae.listen("burst", () => { if (++heard === 10000) resolve(); }));
              const burstStart = performance.now();
              lwjwae.emit("burst-please", "");
              await burst;
              const eventsPerSecond = Math.round(10000 / ((performance.now() - burstStart) / 1000));
              return { bridgeCallMs, rpcCallMs, rpcStreamMBps, eventRoundTripMs, eventsPerSecond, bytes };
              """);
      System.out.println("RPC benchmark: " + result);
      Assertions.assertEquals(32L * 1024 * 1024, result.path("bytes").asLong(), result.toString());
    }
  }

  /**
   * Loads the blank page of the tests, runs {@code body} as an async function, and reads its
   * result.
   */
  private static JsonNode run(Window window, String body) throws Exception {
    return run(window, body, null);
  }

  /**
   * Runs {@code body}, the body of an async function, in the page of {@code window} at {@code url},
   * or the blank page of the application, and returns what it returned. The result comes back by
   * polling rather than through a binding, because a page of a foreign origin can't call Java.
   */
  private static JsonNode run(Window window, String body, String url) throws Exception {
    CompletableFuture<Void> loaded = new CompletableFuture<>();
    window.onLoad(
        event -> {
          if (event.state() == LoadState.FINISHED) {
            loaded.complete(null);
          }
        });
    if (url == null) {
      window.loadResource("rpc/blank.html");
    } else {
      window.navigate(url);
    }
    window.show();
    loaded.get(30, TimeUnit.SECONDS);
    window.eval(
        "(async () => { try { window.__report = JSON.stringify(await (async () => {"
            + body
            + "})()); } catch (error) { window.__report = JSON.stringify({ failed: String(error)"
            + " }); } })();");
    JsonNode result = JSON.readTree(awaitReport(window));
    Assertions.assertFalse(result.has("failed"), "the page script failed: " + result);
    return result;
  }

  /** Polls for the report of {@link #run}, for as long as a benchmark may take. */
  private static String awaitReport(Window window) throws Exception {
    for (int attempt = 0; attempt < 900; attempt++) {
      String value = Loads.eval(window, "String(window.__report)");
      if (!"undefined".equals(value)) {
        return value;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("The page script didn't report");
  }

  /** A plain HTTP server with the blank page, as a development server or a foreign site. */
  private static HttpServer pageServer() throws Exception {
    byte[] page = ResourceUtil.read("rpc/blank.html");
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
          exchange.sendResponseHeaders(200, page.length);
          exchange.getResponseBody().write(page);
          exchange.close();
        });
    server.start();
    return server;
  }
}
