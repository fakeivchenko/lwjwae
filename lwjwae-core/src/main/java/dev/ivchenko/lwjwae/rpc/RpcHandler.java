package dev.ivchenko.lwjwae.rpc;

/**
 * Answers the calls that a page makes with {@code lwjwae.call(name, body)}.
 *
 * <p>A handler runs on a virtual thread of its own, one per call, so it may block. It answers
 * through {@link RpcCall}: once with {@link RpcCall#reply}, or in parts through {@link
 * RpcCall#stream}. A handler that returns without answering answers with an empty body, and one
 * that throws answers with an error: the status and code of an {@link RpcException}, or {@code 500}
 * for anything else. When the page abandons the call, the thread of the handler is interrupted.
 */
@FunctionalInterface
public interface RpcHandler {
  /** Handles one call. */
  void handle(RpcCall call) throws Exception;
}
