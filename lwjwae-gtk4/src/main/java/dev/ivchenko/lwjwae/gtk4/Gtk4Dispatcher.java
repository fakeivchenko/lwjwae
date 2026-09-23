package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.gtk4.binding.Glib;
import dev.ivchenko.lwjwae.gtk4.binding.Gtk;
import dev.ivchenko.lwjwae.gtk4.binding.Signatures;
import dev.ivchenko.lwjwae.ui.EventLoopDispatcher;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The process-wide GTK 4 thread.
 *
 * <p>GTK can only be used from the thread that initialized it, so one daemon thread initializes GTK
 * and then runs the default main context for the lifetime of the process. GTK 4 has no {@code
 * gtk_main()}; a {@code GMainLoop} on the default context does the same job. Work posted from other
 * threads arrives through a {@code g_idle_add} source, the one Glib entry point that's safe to call
 * from any thread.
 *
 * <p>GTK 3 and GTK 4 can't share a process: their symbols and their types clash. This thread is the
 * reason the GTK 4 backend must not run next to the GTK 3 one, which {@link Gtk4BackendProvider}
 * leaves to the lower priority of this backend and to the choice of a single backend per process.
 */
public class Gtk4Dispatcher extends EventLoopDispatcher {
  private static final Gtk4Dispatcher INSTANCE = new Gtk4Dispatcher();

  /**
   * The shared {@code GSourceFunc} stub. The queue in {@link EventLoopDispatcher} carries the work.
   */
  private static final MemorySegment DRAIN_STUB =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Dispatcher.class,
          "drain",
          MethodType.methodType(int.class, MemorySegment.class),
          Signatures.G_SOURCE_FUNC);

  private Gtk4Dispatcher() {
    super("lwjwae-gtk4");
  }

  /**
   * Returns the dispatcher, and starts the GTK thread on first use.
   *
   * @throws IllegalStateException If GTK can't open a display.
   */
  public static Gtk4Dispatcher instance() {
    INSTANCE.start();
    return INSTANCE;
  }

  @Override
  protected void initialize() {
    if (!Gtk.initialize()) {
      throw new IllegalStateException(
          "gtk_init_check() failed: no display available (check DISPLAY / WAYLAND_DISPLAY)");
    }
  }

  @Override
  protected void runEventLoop() {
    Glib.runMainLoop();
  }

  @Override
  protected void wakeUp() {
    Glib.idleAdd(DRAIN_STUB, MemorySegment.NULL);
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static int drain(MemorySegment userData) {
    INSTANCE.drainTasks();
    return Glib.SOURCE_REMOVE;
  }
}
