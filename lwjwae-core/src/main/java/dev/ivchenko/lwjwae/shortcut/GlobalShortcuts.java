package dev.ivchenko.lwjwae.shortcut;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.exception.ShortcutUnavailableException;

/**
 * The shortcuts of the application that work whichever application has the keyboard. {@link
 * dev.ivchenko.lwjwae.Application#shortcuts()} hands them out.
 *
 * <p>A shortcut belongs to the application while it is registered: the key press goes to the
 * handler, not to the application in front. The handler runs off the UI thread, like a handler of
 * the tray, so it may block or call back into a window, often {@link
 * dev.ivchenko.lwjwae.Window#focus()}. Every shortcut goes back to the system when the application
 * closes.
 *
 * <p>Every method is safe to call from any thread.
 */
public interface GlobalShortcuts {
  /**
   * Runs {@code handler} whenever the user presses {@code shortcut}, until the returned handle
   * gives the shortcut back or the application closes.
   *
   * <pre>{@code
   * application.shortcuts().register("CmdOrCtrl+Shift+Space", window::focus);
   * }</pre>
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: {@code RegisterHotKey}. A shortcut that another application holds throws, and so
   *       does one that Windows keeps, such as {@code Win+L}. Holding the keys down runs the
   *       handler once.
   *   <li>macOS: {@code RegisterEventHotKey} of Carbon, which needs no permission of the user. A
   *       shortcut of the system, such as {@code Command+Space} for Spotlight, may still go to the
   *       system first.
   *   <li>Linux, GTK 3: X11: a grab of the key on the root window, which throws when another client
   *       holds it. Wayland: the {@code GlobalShortcuts} portal, which KDE Plasma and GNOME 48 and
   *       later have. The desktop asks the user to confirm the shortcuts the first time and may let
   *       the user pick other keys; the call returns before that, and a shortcut that the user
   *       turns down never runs. Without the portal, the call throws {@link
   *       UnsupportedOperationException}. {@link dev.ivchenko.lwjwae.Window#focus()} from the
   *       handler brings the window to the front where the portal hands on the activation token of
   *       the press, as GNOME does; KDE Plasma hands none on, and the window only asks for
   *       attention in the task bar.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @return The handle that gives the shortcut back. Giving it back twice is harmless.
   * @throws ShortcutUnavailableException If another application, or this one, holds the shortcut.
   * @throws UnsupportedOperationException If the desktop has no global shortcuts, or the backend
   *     has none yet.
   * @throws IllegalStateException If the application is closed.
   */
  EventSubscription register(Shortcut shortcut, Runnable handler);

  /**
   * Runs {@code handler} whenever the user presses {@code shortcut}, written as {@link
   * Shortcut#parse} reads it, such as {@code Ctrl+Shift+K}.
   *
   * @throws IllegalArgumentException If the text isn't a shortcut.
   */
  default EventSubscription register(String shortcut, Runnable handler) {
    return this.register(Shortcut.parse(shortcut), handler);
  }
}
