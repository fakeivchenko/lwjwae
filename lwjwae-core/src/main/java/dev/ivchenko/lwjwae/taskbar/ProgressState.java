package dev.ivchenko.lwjwae.taskbar;

import java.util.Locale;

/** What the progress on the icon of the application says. */
public enum ProgressState {
  /** No progress: the icon is as it always is. */
  NONE,

  /** Work that is this far done. */
  NORMAL,

  /** Work of a length that nobody knows yet. */
  INDETERMINATE,

  /** Work that stopped for now, drawn in yellow on Windows. */
  PAUSED,

  /** Work that failed, drawn in red on Windows. */
  ERROR;

  /** The name that the page uses, such as {@code indeterminate}. */
  public String pageName() {
    return this.name().toLowerCase(Locale.ROOT);
  }

  /** The state of a name that the page uses, or {@code null} for none. */
  public static ProgressState ofPageName(String name) {
    for (ProgressState state : ProgressState.values()) {
      if (state.pageName().equals(name)) {
        return state;
      }
    }
    return null;
  }
}
