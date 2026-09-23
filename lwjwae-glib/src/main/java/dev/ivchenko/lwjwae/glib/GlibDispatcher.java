package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.Signatures;
import dev.ivchenko.lwjwae.ui.EventLoopDispatcher;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * A UI thread that runs the default GLib main context, the part that the GTK 3 and GTK 4 threads
 * share.
 *
 * <p>Work posted from other threads arrives through a {@code g_idle_add} source, the one GLib entry
 * point that's safe to call from any thread; the queue of {@link EventLoopDispatcher} carries the
 * work, and the source only wakes the thread to drain it. A subclass initializes its toolkit and
 * runs the loop, which is where GTK 3 and GTK 4 differ.
 */
public abstract class GlibDispatcher extends EventLoopDispatcher {
  private static final CallbackRegistry<GlibDispatcher> DISPATCHERS = new CallbackRegistry<>();

  /** The shared {@code GSourceFunc} stub; the user data names the dispatcher. */
  private static final MemorySegment DRAIN_STUB =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GlibDispatcher.class,
          "drain",
          MethodType.methodType(int.class, MemorySegment.class),
          Signatures.G_SOURCE_FUNC);

  private final long callbackId;

  /** Creates the dispatcher; its thread is named {@code threadName} and starts on first use. */
  protected GlibDispatcher(String threadName) {
    super(threadName);
    this.callbackId = DISPATCHERS.register(this);
  }

  @Override
  protected final void wakeUp() {
    Glib.idleAdd(DRAIN_STUB, CallbackRegistry.userData(this.callbackId));
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static int drain(MemorySegment userData) {
    GlibDispatcher dispatcher = DISPATCHERS.lookup(userData);
    if (dispatcher != null) {
      dispatcher.drainTasks();
    }
    return Glib.SOURCE_REMOVE;
  }
}
