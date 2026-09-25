package dev.ivchenko.lwjwae.bridge;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The calls that the page of one window makes over the message channel: it reads the messages,
 * turns a call into a {@link MessageRpcExchange}, and keeps the running ones, so that the page can
 * cancel them and the window can drop them when it closes.
 *
 * <p>A message that doesn't parse, or doesn't carry the token of the window, is reported and
 * dropped, not thrown: the caller is a native callback, and a frame of a foreign origin, which can
 * post to the channel but never learned the token, mustn't reach a handler.
 */
public final class MessageRpcCalls {
  private static final String CANCEL_TAG = "\u0001rpc-cancel";

  private final Window window;
  private final RpcMessageChannel channel;
  private final String token;
  private final Map<String, MessageRpcExchange> running = new ConcurrentHashMap<>();

  /**
   * Starts with no call running.
   *
   * @param window The window whose page calls.
   * @param channel The way back to the page.
   * @param token The secret that the bootstrap hands to a trusted document.
   */
  public MessageRpcCalls(Window window, RpcMessageChannel channel, String token) {
    this.window = window;
    this.channel = channel;
    this.token = token;
  }

  /**
   * Reads one message from the page.
   *
   * @return The exchange of a new call, to serve; {@code null} for a cancellation or a message that
   *     was dropped.
   */
  public MessageRpcExchange receive(String message) {
    String[] fields = message.split(BridgeProtocol.SEPARATOR, 8);
    boolean call = fields[0].equals(MessageRpcExchange.TAG) && fields.length == 8;
    boolean cancel = fields[0].equals(CANCEL_TAG) && fields.length == 4;
    if (!call && !cancel) {
      ThrowableUtil.report(new IllegalStateException("Malformed bridge message"));
      return null;
    }
    if (!fields[1].equals(this.token)) {
      ThrowableUtil.report(new IllegalStateException("Bridge message without the window token"));
      return null;
    }
    if (cancel) {
      MessageRpcExchange exchange =
          this.running.remove(MessageRpcExchange.key(fields[2], fields[3]));
      if (exchange != null) {
        exchange.cancel();
      }
      return null;
    }
    String[] callFields = new String[6];
    System.arraycopy(fields, 2, callFields, 0, 6);
    MessageRpcExchange exchange =
        new MessageRpcExchange(this, this.channel, this.window, callFields);
    this.running.put(exchange.key(), exchange);
    return exchange;
  }

  /** Cancels every running call: the window is gone. */
  public void cancelAll() {
    this.running.values().forEach(MessageRpcExchange::cancel);
    this.running.clear();
  }

  /** Drops a call that answered in full from the calls that the page can still cancel. */
  void forget(MessageRpcExchange exchange) {
    this.running.remove(exchange.key(), exchange);
  }
}
