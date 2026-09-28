package dev.ivchenko.lwjwae.bridge;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * An {@link RpcExchange} over the message channel of the engine: how every call but {@code
 * lwjwae.call} reaches Java, and on WebView2 that one too.
 *
 * <p>The page posts {@code \u0001rpc␟token␟doc␟id␟name␟type␟encoding␟body} through the channel that
 * the backend names in {@code AbstractWindow.bridgeTransportScript()}; the body is text, or bytes
 * in Base64. {@code token} is a secret of the window that the bootstrap hands only to a document of
 * a trusted origin, and it stands in for the {@code Origin} header, which a message doesn't carry:
 * every frame of the window can post to the channel, but only a trusted one knows what to post.
 * {@code doc} is a token of the document, so the answers to a document that the window left don't
 * reach the next one.
 *
 * <p>The answer goes back through {@link RpcMessageChannel#post}: {@code
 * \u0001rpc␟doc␟id␟r␟status␟type␟encoding␟body} when it is whole, the common case, which costs one
 * message; otherwise {@code h␟status␟type}, one message per part, and {@code e␟count}. A part of
 * {@value #BUFFER_THRESHOLD} bytes or more goes through {@link RpcMessageChannel#postBuffer}, which
 * WebView2 answers with a shared buffer and no encoding. Every part carries its number, because a
 * text message and a shared buffer arrive as different events. {@code
 * \u0001rpc-cancel␟token␟doc␟id} abandons a call.
 *
 * <p>At most {@value #IN_FLIGHT} messages of one call wait for the UI thread: backpressure without
 * a round trip per part.
 */
public final class MessageRpcExchange implements RpcExchange {
  static final String TAG = "\u0001rpc";
  private static final String SEPARATOR = BridgeProtocol.SEPARATOR;
  private static final int BUFFER_THRESHOLD = 16 * 1024;
  private static final int IN_FLIGHT = 16;

  private final MessageRpcCalls calls;
  private final RpcMessageChannel channel;
  private final Window window;
  private final String doc;
  private final String id;
  private final String path;
  private final String contentType;
  private final byte[] body;
  private final Semaphore inFlight = new Semaphore(IN_FLIGHT);

  private volatile boolean cancelled;
  private volatile Runnable onCancel;
  private int parts;

  /**
   * Takes a call from its message.
   *
   * @param calls The running calls of the window, which this one leaves once it has answered.
   * @param fields The fields of a call message after the token: {@code doc}, {@code id}, the name,
   *     the media type, the encoding, and the body.
   */
  MessageRpcExchange(
      MessageRpcCalls calls, RpcMessageChannel channel, Window window, String[] fields) {
    this.calls = calls;
    this.channel = channel;
    this.window = window;
    this.doc = fields[0];
    this.id = fields[1];
    this.path = PATH_PREFIX + fields[2];
    this.contentType = fields[3].isEmpty() ? null : fields[3];
    this.body =
        fields[4].equals("b")
            ? Base64.getDecoder().decode(fields[5])
            : fields[5].getBytes(StandardCharsets.UTF_8);
  }

  /** The key of this call among the running calls of the window. */
  String key() {
    return MessageRpcExchange.key(this.doc, this.id);
  }

  static String key(String doc, String id) {
    return doc + SEPARATOR + id;
  }

  @Override
  public String method() {
    return "POST";
  }

  @Override
  public String path() {
    return this.path;
  }

  /** {@code Content-Type} only; the token vouched for the origin already. */
  @Override
  public String header(String name) {
    return "Content-Type".equalsIgnoreCase(name) ? this.contentType : null;
  }

  @Override
  public byte[] body() {
    return this.body;
  }

  @Override
  public void respond(int status, Map<String, String> headers) {
    this.post(this.message("h", String.valueOf(status), headers.getOrDefault("Content-Type", "")));
  }

  @Override
  public void reply(int status, Map<String, String> headers, byte[] body) {
    this.calls.forget(this);
    String type = headers.getOrDefault("Content-Type", "");
    String encoded =
        MessageRpcExchange.isText(type)
            ? "s" + SEPARATOR + new String(body, StandardCharsets.UTF_8)
            : "b" + SEPARATOR + Base64.getEncoder().encodeToString(body);
    this.post(this.message("r", String.valueOf(status), type, encoded));
  }

  @Override
  public boolean write(byte[] part) {
    if (this.cancelled) {
      return false;
    }
    String seq = String.valueOf(this.parts++);
    if (part.length >= BUFFER_THRESHOLD) {
      String data = "{\"doc\":\"" + this.doc + "\",\"id\":\"" + this.id + "\",\"seq\":" + seq + "}";
      Supplier<String> fallback =
          () -> this.message("d", seq, Base64.getEncoder().encodeToString(part));
      this.enqueue(() -> this.channel.postBuffer(part, data, fallback));
    } else {
      this.post(this.message("d", seq, Base64.getEncoder().encodeToString(part)));
    }
    return true;
  }

  @Override
  public void end() {
    this.calls.forget(this);
    this.post(this.message("e", String.valueOf(this.parts)));
  }

  @Override
  public void onCancel(Runnable action) {
    this.onCancel = action;
    if (this.cancelled) {
      action.run();
    }
  }

  /** The page abandoned the call, or the window can't take the answer any more. */
  void cancel() {
    this.cancelled = true;
    Runnable action = this.onCancel;
    if (action != null) {
      action.run();
    }
  }

  private String message(String kind, String... fields) {
    return TAG
        + SEPARATOR
        + this.doc
        + SEPARATOR
        + this.id
        + SEPARATOR
        + kind
        + SEPARATOR
        + String.join(SEPARATOR, fields);
  }

  private void post(String message) {
    this.enqueue(() -> this.channel.post(message));
  }

  /** Posts one message, waiting while {@value #IN_FLIGHT} of this call are on their way. */
  private void enqueue(Supplier<CompletableFuture<?>> send) {
    if (this.window.isClosed()) {
      this.cancel();
      return;
    }
    this.inFlight.acquireUninterruptibly();
    CompletableFuture<?> sent;
    try {
      sent = send.get();
    } catch (RuntimeException _) {
      this.inFlight.release();
      this.cancel();
      return;
    }
    sent.whenComplete((_, _) -> this.inFlight.release());
  }

  /** Whether an answer of {@code type} can travel as text: UTF-8 in, UTF-8 out. */
  private static boolean isText(String type) {
    String lower = type.toLowerCase(Locale.ROOT);
    return lower.startsWith("text/")
        || lower.contains("charset=utf-8")
        || lower.startsWith("application/json");
  }
}
