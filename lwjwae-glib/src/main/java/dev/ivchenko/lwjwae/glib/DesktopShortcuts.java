package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.shortcut.Shortcut;

/** The global shortcuts of an application on Linux: key grabs on X11, the portal on Wayland. */
public interface DesktopShortcuts {
  /**
   * Binds {@code shortcut}; {@code pressed} runs on the GTK thread on every press.
   *
   * @return What gives the shortcut back.
   */
  EventSubscription bind(Shortcut shortcut, Runnable pressed);

  /** Gives every shortcut back. */
  void close();
}
