package dev.ivchenko.lwjwae.bridge;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * How RPC messages from Java reach the page of a window: the way back of the message channel, see
 * {@link MessageRpcExchange}.
 *
 * <p>Every engine can evaluate a script, so the default of a window evaluates {@code
 * receive(message)} of the bootstrap. An engine that can post to the page directly supplies its own
 * channel, which costs less per message, and one that can share memory with the page also overrides
 * {@link #postBuffer}.
 */
@FunctionalInterface
public interface RpcMessageChannel {
  /**
   * Hands one message to the page, from any thread.
   *
   * @return What completes once the message is on its way, which is what holds a busy call back.
   */
  CompletableFuture<?> post(String message);

  /**
   * Hands one large part of an answer to the page as bytes, with {@code additionalDataAsJson} that
   * tells the page which call and part it is. The default posts {@code fallback}, the same part as
   * a Base64 message.
   */
  default CompletableFuture<?> postBuffer(
      byte[] data, String additionalDataAsJson, Supplier<String> fallback) {
    return this.post(fallback.get());
  }
}
