package dev.ivchenko.lwjwae.event;

/**
 * The handle that {@code listen} and {@code once} return: what removes the listener again.
 *
 * <p>Calling {@link #unlisten()} more than once is harmless, and a {@code once} listener removes
 * itself, so the handle is only needed when a listener has to go before its event came.
 */
@FunctionalInterface
public interface EventSubscription {
  /** Removes the listener. Deliveries that already started still reach it. */
  void unlisten();
}
