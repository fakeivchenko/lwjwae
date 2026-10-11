package dev.ivchenko.lwjwae.event;

import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The Java listeners of one kind of event, and the virtual thread that they hear it on.
 *
 * <p>The listeners hear one event after the other, in the order of {@link #deliver}, so a slow one
 * doesn't hold up the thread that reports the event, and none hears two events out of order. An
 * event reaches the listeners registered when it's delivered; one that registers later doesn't hear
 * it. A listener that throws is reported and the others still hear the event.
 *
 * <p>After {@link #shutdown()}, deliveries already queued still run and new ones are dropped. A
 * delivery that races the shutdown is dropped as well rather than failing the reporter: an event
 * reported while its owner closes has nobody left to hear it either way.
 *
 * @param <E> The type of the event.
 */
public final class Listeners<E> {
  private final Map<Long, Consumer<E>> listeners = new ConcurrentHashMap<>();
  private final AtomicLong listenerIds = new AtomicLong();
  private final ExecutorService executor;

  /** Creates the listeners with their delivery thread, named {@code threadName}. */
  public Listeners(String threadName) {
    this.executor =
        Executors.newSingleThreadExecutor(Thread.ofVirtual().name(threadName).factory());
  }

  /** Registers {@code listener} for every event delivered from now on. */
  public EventSubscription listen(Consumer<E> listener) {
    long id = this.listenerIds.incrementAndGet();
    this.listeners.put(id, listener);
    return () -> this.listeners.remove(id);
  }

  /** Whether no listener is registered. */
  public boolean isEmpty() {
    return this.listeners.isEmpty();
  }

  /** Hands {@code event} to the listeners on the delivery thread. Does nothing after shutdown. */
  public void deliver(E event) {
    if (this.listeners.isEmpty() || this.executor.isShutdown()) {
      return;
    }
    List<Consumer<E>> current = List.copyOf(this.listeners.values());
    try {
      this.executor.execute(() -> current.forEach(listener -> Listeners.tell(listener, event)));
    } catch (RejectedExecutionException _) {
      // Shut down between the check above and here: the owner closed, drop the event.
    }
  }

  /** Hands {@code event} to the listeners on the calling thread, and returns once all heard it. */
  public void deliverHere(E event) {
    List.copyOf(this.listeners.values()).forEach(listener -> Listeners.tell(listener, event));
  }

  /** Stops accepting deliveries. Deliveries already queued still run. */
  public void shutdown() {
    this.executor.shutdown();
  }

  /** Hands {@code event} to {@code listener}, and reports what it throws. */
  public static <E> void tell(Consumer<E> listener, E event) {
    try {
      listener.accept(event);
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
