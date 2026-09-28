package dev.ivchenko.lwjwae.bridge;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import dev.ivchenko.lwjwae.rpc.RpcCall;
import dev.ivchenko.lwjwae.rpc.RpcException;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import dev.ivchenko.lwjwae.rpc.RpcHandler;
import dev.ivchenko.lwjwae.rpc.RpcStream;
import dev.ivchenko.lwjwae.util.ScriptUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * An {@link RpcCall} on top of the {@link RpcExchange} of a backend.
 *
 * <p>The answer goes out once: a {@code reply}, a stream, an empty answer when the handler returns
 * without one, or an error when it throws. A cancellation from the engine marks the call and
 * interrupts the thread of the handler; a write after it answers {@code false}.
 */
public final class ExchangeRpcCall implements RpcCall {
  private final RpcExchange exchange;
  private final String name;
  private final Window window;
  private final Supplier<BridgeCodec> codec;
  private final Map<String, String> cors;
  private final AtomicBoolean answered = new AtomicBoolean();
  private final AtomicBoolean ended = new AtomicBoolean();

  private volatile boolean cancelled;
  private volatile Thread thread;

  /**
   * Wraps a call that the backend received as {@code exchange}.
   *
   * @param exchange The request as the backend received it, and the way back.
   * @param name The name that the page called.
   * @param window The window whose page made the call.
   * @param codec The codec of the application, asked only when the call needs one; it throws {@link
   *     IllegalStateException} when there is none.
   * @param cors The CORS headers that every answer carries, empty for a message call.
   */
  public ExchangeRpcCall(
      RpcExchange exchange,
      String name,
      Window window,
      Supplier<BridgeCodec> codec,
      Map<String, String> cors) {
    this.exchange = exchange;
    this.name = name;
    this.window = window;
    this.codec = codec;
    this.cors = cors;
  }

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public Window window() {
    return this.window;
  }

  @Override
  public byte[] body() {
    return this.exchange.body();
  }

  @Override
  public String contentType() {
    return this.exchange.header("Content-Type");
  }

  @Override
  public String text() {
    return new String(this.body(), StandardCharsets.UTF_8);
  }

  @Override
  public <T> T value(Class<T> type) {
    return this.codec.get().decode(this.text(), type);
  }

  @Override
  public void reply(byte[] body, String contentType) {
    this.answerWhole(200, contentType, body);
  }

  @Override
  public void reply(String text) {
    this.reply(text.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
  }

  @Override
  public void replyValue(Object value) {
    String encoded = this.codec.get().encode(value);
    this.reply(encoded.getBytes(StandardCharsets.UTF_8), BridgeProtocol.VALUE_TYPE);
  }

  @Override
  public RpcStream stream(String contentType) {
    this.start(200, contentType);
    return new RpcStream() {
      @Override
      public boolean write(byte[] part) {
        if (ExchangeRpcCall.this.cancelled) {
          return false;
        }
        if (!ExchangeRpcCall.this.exchange.write(part)) {
          ExchangeRpcCall.this.cancel();
          return false;
        }
        return true;
      }

      @Override
      public void close() {
        ExchangeRpcCall.this.end();
      }
    };
  }

  @Override
  public boolean isCancelled() {
    return this.cancelled;
  }

  /** Runs {@code handler} for this call on the calling thread. */
  public void run(RpcHandler handler) {
    this.thread = Thread.currentThread();
    try {
      handler.handle(this);
      if (this.answered.compareAndSet(false, true) && this.ended.compareAndSet(false, true)) {
        this.exchange.reply(204, this.cors, new byte[0]);
      }
      this.end();
    } catch (Throwable failure) {
      this.fail(failure);
    } finally {
      this.thread = null;
      // Don't let a cancellation leak into whatever runs next on this thread.
      Thread.interrupted();
    }
  }

  /** The page abandoned the call. */
  public void cancel() {
    this.cancelled = true;
    Thread running = this.thread;
    if (running != null) {
      running.interrupt();
    }
  }

  private void start(int status, String contentType) {
    if (!this.answered.compareAndSet(false, true)) {
      throw new IllegalStateException("The call " + this.name + " is answered already");
    }
    Map<String, String> headers = new LinkedHashMap<>(this.cors);
    headers.put("Content-Type", contentType);
    this.exchange.respond(status, headers);
  }

  private void answerWhole(int status, String contentType, byte[] body) {
    if (!this.answered.compareAndSet(false, true)) {
      throw new IllegalStateException("The call " + this.name + " is answered already");
    }
    this.ended.set(true);
    Map<String, String> headers = new LinkedHashMap<>(this.cors);
    headers.put("Content-Type", contentType);
    this.exchange.reply(status, headers, body);
  }

  private void end() {
    if (this.ended.compareAndSet(false, true)) {
      this.exchange.end();
    }
  }

  private void fail(Throwable failure) {
    if (!this.answered.get()) {
      int status = failure instanceof RpcException rpc ? rpc.getStatus() : 500;
      String code = failure instanceof RpcException rpc ? rpc.getCode() : "internal";
      if (!(failure instanceof RpcException) && !this.cancelled) {
        ThrowableUtil.report(failure);
      }
      this.answerWhole(
          status,
          "application/json",
          ExchangeRpcCall.errorJson(code, ExchangeRpcCall.rootMessage(failure))
              .getBytes(StandardCharsets.UTF_8));
    } else if (!this.cancelled) {
      // The stream is open: the status is gone, so the page only sees the answer end early.
      ThrowableUtil.report(failure);
    }
    this.end();
  }

  /** The message of the innermost cause, or its type when it has none. */
  private static String rootMessage(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null && !(cause instanceof RpcException)) {
      cause = cause.getCause();
    }
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  /** The body of an error answer: {@code {"code": ..., "error": ...}}. */
  public static String errorJson(String code, String message) {
    return "{\"code\":" + ScriptUtil.quote(code) + ",\"error\":" + ScriptUtil.quote(message) + "}";
  }
}
