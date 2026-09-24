package dev.ivchenko.lwjwae.rpc;

/**
 * An answer that the page reads part by part, while the handler still produces it.
 *
 * <p>A part reaches the page as soon as it's written. {@link #write} blocks while the engine is
 * behind, where the engine applies backpressure, and answers {@code false} once the page abandoned
 * the call, which is the signal for the handler to stop.
 */
public interface RpcStream extends AutoCloseable {
  /**
   * Hands {@code part} to the page.
   *
   * @return False if the page abandoned the call: nothing more will be read.
   */
  boolean write(byte[] part);

  /** Ends the answer. This method is idempotent. */
  @Override
  void close();
}
