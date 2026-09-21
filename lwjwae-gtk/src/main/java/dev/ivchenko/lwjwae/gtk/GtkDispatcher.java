package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.gtk.binding.Glib;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.ui.EventLoopDispatcher;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The process-wide GTK thread.
 *
 * <p>GTK can only be used from the thread that ran {@code gtk_init}, so one daemon thread
 * initializes GTK and then stays in {@code gtk_main()} for the lifetime of the process. Work posted
 * from other threads arrives through a {@code g_idle_add} source, the one Glib entry point that's
 * safe to call from any thread.
 */
public class GtkDispatcher extends EventLoopDispatcher {
  private static final GtkDispatcher INSTANCE = new GtkDispatcher();

  /**
   * The shared {@code GSourceFunc} stub. The queue in {@link EventLoopDispatcher} carries the work.
   */
  private static final MemorySegment DRAIN_STUB =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkDispatcher.class,
          "drain",
          MethodType.methodType(int.class, MemorySegment.class),
          Signatures.G_SOURCE_FUNC);

  private GtkDispatcher() {
    super("lwjwae-gtk");
  }

  /**
   * Returns the dispatcher, and starts the GTK thread on first use.
   *
   * @throws IllegalStateException If GTK can't open a display.
   */
  public static GtkDispatcher instance() {
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
    Gtk.main();
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
