package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.glib.GlibDispatcher;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;

/**
 * The process-wide GTK thread.
 *
 * <p>GTK can only be used from the thread that ran {@code gtk_init}, so one daemon thread
 * initializes GTK and then stays in {@code gtk_main()} for the lifetime of the process. {@link
 * GlibDispatcher} brings the work posted from other threads in.
 */
public class GtkDispatcher extends GlibDispatcher {
  private static final GtkDispatcher INSTANCE = new GtkDispatcher();

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
}
