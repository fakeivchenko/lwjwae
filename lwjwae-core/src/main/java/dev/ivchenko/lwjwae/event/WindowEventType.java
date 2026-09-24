package dev.ivchenko.lwjwae.event;

import java.util.Locale;

/** What changed about a window. */
public enum WindowEventType {
  /** The content area has a new size. */
  RESIZED,

  /** The frame has a new place on the screen. Wayland and GTK 4 never report one. */
  MOVED,

  /** The window took the keyboard focus. */
  FOCUSED,

  /** The window lost the keyboard focus. */
  BLURRED,

  /** The window was minimized. Wayland never reports one. */
  MINIMIZED,

  /** The window came back from minimized. */
  UNMINIMIZED,

  /** The window was maximized. */
  MAXIMIZED,

  /** The window came back from maximized. */
  UNMAXIMIZED,

  /** The window went into full screen. */
  FULLSCREEN_ENTERED,

  /** The window left full screen. */
  FULLSCREEN_EXITED;

  /** The name that the page sees: {@code resized}, {@code fullscreenEntered}. */
  public String pageName() {
    String[] words = this.name().toLowerCase(Locale.ROOT).split("_");
    StringBuilder name = new StringBuilder(words[0]);
    for (int i = 1; i < words.length; i++) {
      name.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
    }
    return name.toString();
  }
}
