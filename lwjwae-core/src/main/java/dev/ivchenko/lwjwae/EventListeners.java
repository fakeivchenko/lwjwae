package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The Java event listeners of one window or of one application, and the thread that they run on.
 *
 * <p>Listeners run on one virtual thread, in order, so a listener never sees the second event of a
 * name before the first. A bound handler gets a thread of its own instead, because a call has a
 * promise waiting on it and no order to keep.
 *
 * <p>After {@link #shutdown()}, deliveries already queued still run and new ones are dropped. A
 * delivery that races the shutdown is dropped as well rather than failing the emitter, because an
 * event emitted while its owner closes has nobody left to hear it either way.
 */
final class EventListeners {
  private final Map<String, Map<Long, Consumer<Event>>> listeners = new ConcurrentHashMap<>();
  private final AtomicLong listenerIds = new AtomicLong();
  private final AtomicLong eventIds = new AtomicLong();
  private final ExecutorService executor;

  /** Creates the listener table with its delivery thread, named {@code threadName}. */
  EventListeners(String threadName) {
    this.executor =
        Executors.newSingleThreadExecutor(Thread.ofVirtual().name(threadName).factory());
  }

  EventSubscription listen(String name, Consumer<Event> listener) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(listener, "listener");
    long id = this.listenerIds.incrementAndGet();
    this.listeners.computeIfAbsent(name, _ -> new ConcurrentHashMap<>()).put(id, listener);
    return () -> this.unlisten(name, id);
  }

  EventSubscription once(String name, Consumer<Event> listener) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(listener, "listener");
    long id = this.listenerIds.incrementAndGet();
    this.listeners
        .computeIfAbsent(name, _ -> new ConcurrentHashMap<>())
        .put(
            id,
            event -> {
              // Only the first delivery that removes the entry runs the listener.
              if (this.unlisten(name, id)) {
                listener.accept(event);
              }
            });
    return () -> this.unlisten(name, id);
  }

  /** Runs the listeners of {@code name} on the delivery thread. Does nothing after shutdown. */
  void deliver(String name, String payload, boolean typed, Window window) {
    Map<Long, Consumer<Event>> named = this.listeners.get(name);
    if (named == null || named.isEmpty() || this.executor.isShutdown()) {
      return;
    }
    Event event = new Event(name, this.eventIds.incrementAndGet(), payload, typed, window);
    try {
      this.executor.execute(
          () -> {
            for (Consumer<Event> listener : named.values()) {
              try {
                listener.accept(event);
              } catch (Throwable t) {
                ThrowableUtil.report(t);
              }
            }
          });
    } catch (RejectedExecutionException _) {
      // Shut down between the check above and here: the owner closed, drop the event.
    }
  }

  /** Stops accepting deliveries. Deliveries already queued still run. */
  void shutdown() {
    this.executor.shutdown();
  }

  /**
   * Decodes the payload of an event for a typed listener.
   *
   * <p>Suppressed warnings: {@code unchecked}: the cast is guarded by {@code type == String.class},
   * so the value is a {@code String} whenever it's cast to {@code T}.
   */
  @SuppressWarnings("unchecked")
  static <T> Consumer<Event> decoding(BridgeCodec codec, Class<T> type, Consumer<T> listener) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(listener, "listener");
    return event ->
        listener.accept(
            !event.typed() && type == String.class
                ? (T) event.payload()
                : codec.decode(event.payload(), type));
  }

  private boolean unlisten(String name, long id) {
    Map<Long, Consumer<Event>> named = this.listeners.get(name);
    return named != null && named.remove(id) != null;
  }
}
