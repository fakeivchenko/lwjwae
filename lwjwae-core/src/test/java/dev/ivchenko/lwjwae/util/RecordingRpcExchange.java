package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.rpc.RpcExchange;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;

/**
 * A GET of a resource with the headers that a test gives it, which keeps the answer for the test to
 * read once {@link #awaitEnd} returns.
 */
final class RecordingRpcExchange implements RpcExchange {
  private final Map<String, String> requestHeaders;
  private final ByteArrayOutputStream written = new ByteArrayOutputStream();
  private final CountDownLatch ended = new CountDownLatch(1);

  private volatile int status;
  private volatile Map<String, String> headers = Map.of();

  RecordingRpcExchange(Map<String, String> requestHeaders) {
    this.requestHeaders = requestHeaders;
  }

  /** Waits for {@link #end}, which {@link ResourceUtil#stream} calls from a thread of its own. */
  void awaitEnd(Duration timeout) throws InterruptedException {
    Assertions.assertTrue(this.ended.await(timeout.toMillis(), TimeUnit.MILLISECONDS));
  }

  int status() {
    return this.status;
  }

  Map<String, String> headers() {
    return this.headers;
  }

  synchronized byte[] written() {
    return this.written.toByteArray();
  }

  @Override
  public String method() {
    return "GET";
  }

  @Override
  public String path() {
    return "/";
  }

  @Override
  public String header(String name) {
    return this.requestHeaders.entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  @Override
  public byte[] body() {
    return new byte[0];
  }

  @Override
  public void respond(int status, Map<String, String> headers) {
    this.status = status;
    this.headers = Map.copyOf(headers);
  }

  @Override
  public synchronized boolean write(byte[] part) {
    this.written.writeBytes(part);
    return true;
  }

  @Override
  public void end() {
    this.ended.countDown();
  }

  @Override
  public void onCancel(Runnable action) {}
}
