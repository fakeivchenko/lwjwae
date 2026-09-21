package dev.ivchenko.lwjwae.foreign;

import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Maps native {@code void*} user data to Java objects.
 *
 * <p>C callbacks carry no captured state, only an opaque pointer. Handing out real object addresses
 * isn't an option under a moving garbage collector, so each object is registered under a generated
 * ID, and the ID travels as the user data pointer. As a result, one static upcall stub serves every
 * instance, which also keeps the set of stubs fixed, as {@code native-image} requires.
 *
 * @param <T> The type that the callbacks dispatch to.
 */
public class CallbackRegistry<T> {
  private final Map<Long, T> entries = new ConcurrentHashMap<>();
  private final AtomicLong ids = new AtomicLong();

  /** Registers {@code value} and returns the ID to pass as user data. The ID is never zero. */
  public long register(T value) {
    long id = this.ids.incrementAndGet();
    this.entries.put(id, value);
    return id;
  }

  /**
   * Returns the entry registered under {@code id}, or {@code null} if the entry was unregistered.
   */
  public T lookup(long id) {
    return this.entries.get(id);
  }

  /**
   * Returns the entry that the user data pointer of a callback refers to, or {@code null} if the
   * entry is gone.
   */
  public T lookup(MemorySegment userData) {
    return this.lookup(userData.address());
  }

  /**
   * Removes and returns the entry under {@code id}. Later callbacks that carry the ID find nothing.
   */
  public T unregister(long id) {
    return this.entries.remove(id);
  }

  /** The same as {@link #unregister(long)}, for the user data pointer of a callback. */
  public T unregister(MemorySegment userData) {
    return this.unregister(userData.address());
  }

  /** Turns an ID from {@link #register} into the {@code void*} to pass to native code. */
  public static MemorySegment userData(long id) {
    return MemorySegment.ofAddress(id);
  }
}
