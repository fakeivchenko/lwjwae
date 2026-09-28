package dev.ivchenko.lwjwae;

import java.util.Objects;

/**
 * A screen of the desktop, as {@link Application#screens()} lists them.
 *
 * <p>Platforms:
 *
 * <ul>
 *   <li>Windows: The name is the name of the display device, such as {@code \\.\DISPLAY1}. The
 *       scale is the one of the display settings, 1.5 for 150 %; a process that doesn't declare
 *       itself aware of it, as a native image without a manifest, sees 1.0 and every size scaled by
 *       Windows.
 *   <li>macOS: Points, with a scale of 2.0 on a Retina screen. The work area leaves out the menu
 *       bar and the Dock.
 *   <li>Linux, GTK 3: The name is the manufacturer and the model, which can be codes from the
 *       monitor rather than names. The scale is a whole number: Wayland rounds a fractional scale
 *       up, 2.0 for 125 %. X11: as described. Wayland: the first screen stands for the primary one,
 *       and the work area is the whole screen, since the compositor doesn't tell a client where its
 *       panels are.
 *   <li>Linux, GTK 4: The name is the description of the monitor from GTK 4.10 on, its connector,
 *       such as {@code HDMI-1}, before. The first screen stands for the primary one, since GTK 4
 *       has none, and the work area is the whole screen. The scale is fractional from GTK 4.14 on,
 *       a whole number before.
 * </ul>
 *
 * @param name A name of the screen for a person, or empty where the platform has none.
 * @param bounds The whole screen.
 * @param workArea The part of the screen that windows get: the screen minus the taskbar, the menu
 *     bar, the Dock, or the panels of the desktop.
 * @param scale How many physical pixels one unit of {@link WindowSize} and {@link WindowPosition}
 *     is on this screen: 1.0 at the standard density, 2.0 on a Retina screen.
 * @param primary Whether this is the main screen, the one that holds the menu bar or the taskbar
 *     and the origin of the coordinates.
 */
public record Screen(
    String name, ScreenArea bounds, ScreenArea workArea, double scale, boolean primary) {
  public Screen {
    if (name == null) {
      name = "";
    }
    Objects.requireNonNull(bounds, "bounds");
    if (workArea == null) {
      workArea = bounds;
    }
    if (scale <= 0) {
      scale = 1;
    }
  }
}
