package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ResourceUtilTest {
  @Test
  void buildsAppSchemeUrls() {
    Assertions.assertEquals("app://local/app/index.html", ResourceUtil.url("app/index.html"));
    Assertions.assertEquals("app://local/app/index.html", ResourceUtil.url("/app/index.html"));
  }

  @Test
  void readsClasspathResourcesWithOrWithoutLeadingSlash() {
    String expected = "hello from the classpath\n";
    Assertions.assertEquals(
        expected, new String(ResourceUtil.read("fixtures/hello.txt"), StandardCharsets.UTF_8));
    Assertions.assertEquals(
        expected, new String(ResourceUtil.read("/fixtures/hello.txt"), StandardCharsets.UTF_8));
  }

  @Test
  void missingResourceIsTypedFailure() {
    ResourceNotFoundException failure =
        Assertions.assertThrows(
            ResourceNotFoundException.class, () -> ResourceUtil.read("fixtures/nope.txt"));
    Assertions.assertTrue(failure.getMessage().contains("fixtures/nope.txt"));
  }

  @Test
  void pageReachesNoResourceOutsideItsPathsByDotSegments() {
    Assertions.assertTrue(ResourceUtil.servedSize("fixtures/hello.txt") > 0);
    String services = "META-INF/services/dev.ivchenko.lwjwae.BackendProvider";
    for (String path :
        new String[] {
          services,
          "./" + services,
          "fixtures/../" + services,
          "fixtures//../" + services,
          ResourceUtil.servedPath("fixtures%2F..%2F" + services.replace("/", "%2F")),
          "fixtures/../fixtures/hello.txt",
          "fixtures\\hello.txt",
          "dev/ivchenko/lwjwae/util/ResourceUtilTest.class"
        }) {
      Assertions.assertThrows(
          ResourceNotFoundException.class, () -> ResourceUtil.servedSize(path), path);
    }
  }

  @Test
  void streamsTheWholeResourceWithItsLength() throws InterruptedException {
    RecordingRpcExchange exchange = new RecordingRpcExchange(Map.of());
    ResourceUtil.stream(exchange, "fixtures/hello.txt");
    exchange.awaitEnd(Duration.ofSeconds(5));
    Assertions.assertEquals(200, exchange.status());
    Assertions.assertEquals("25", exchange.headers().get("Content-Length"));
    Assertions.assertEquals("bytes", exchange.headers().get("Accept-Ranges"));
    Assertions.assertEquals(
        "hello from the classpath\n", new String(exchange.written(), StandardCharsets.UTF_8));
  }

  @Test
  void streamsOnlyTheRangeThatTheRequestAsksFor() throws InterruptedException {
    RecordingRpcExchange exchange = new RecordingRpcExchange(Map.of("Range", "bytes=6-9"));
    ResourceUtil.stream(exchange, "fixtures/hello.txt");
    exchange.awaitEnd(Duration.ofSeconds(5));
    Assertions.assertEquals(206, exchange.status());
    Assertions.assertEquals("bytes 6-9/25", exchange.headers().get("Content-Range"));
    Assertions.assertEquals("4", exchange.headers().get("Content-Length"));
    Assertions.assertEquals("from", new String(exchange.written(), StandardCharsets.UTF_8));
  }

  @Test
  void rangePastTheEndIsAnsweredWithItsSizeAndNoBody() throws InterruptedException {
    RecordingRpcExchange exchange = new RecordingRpcExchange(Map.of("Range", "bytes=25-"));
    ResourceUtil.stream(exchange, "fixtures/hello.txt");
    exchange.awaitEnd(Duration.ofSeconds(5));
    Assertions.assertEquals(416, exchange.status());
    Assertions.assertEquals("bytes */25", exchange.headers().get("Content-Range"));
    Assertions.assertEquals(0, exchange.written().length);
  }
}
