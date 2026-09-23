package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.glib.GlibDispatcher;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk4.binding.Gtk;

/**
 * The process-wide GTK 4 thread.
 *
 * <p>GTK can only be used from the thread that initialized it, so one daemon thread initializes GTK
 * and then runs the default main context for the lifetime of the process. GTK 4 has no {@code
 * gtk_main()}; a {@code GMainLoop} on the default context does the same job. {@link GlibDispatcher}
 * brings the work posted from other threads in.
 *
 * <p>GTK 3 and GTK 4 can't share a process: their symbols and their types clash. This thread is the
 * reason the GTK 4 backend must not run next to the GTK 3 one, which {@link Gtk4BackendProvider}
 * leaves to the lower priority of this backend and to the choice of a single backend per process.
 */
public class Gtk4Dispatcher extends GlibDispatcher {
  private static final Gtk4Dispatcher INSTANCE = new Gtk4Dispatcher();

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
}
