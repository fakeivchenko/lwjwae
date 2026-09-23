package dev.ivchenko.lwjwae.tray;

/**
 * One entry of the menu of a tray icon: a labeled action, or a separator.
 *
 * <p>The action runs off the UI thread, so it may block or call back into the window. A {@code
 * null} label makes the entry a separator, and {@link #separator()} says so in the code.
 *
 * @param label The text of the entry, or {@code null} for a separator.
 * @param action What happens when the user picks the entry. Ignored for a separator; a {@code null}
 *     action makes the entry do nothing.
 * @param enabled Whether the user can pick the entry. A disabled entry is drawn grayed out.
 */
public record TrayMenuItem(String label, Runnable action, boolean enabled) {
  /** Creates an enabled entry. */
  public TrayMenuItem(String label, Runnable action) {
    this(label, action, true);
  }

  /** Creates a line between two groups of entries. */
  public static TrayMenuItem separator() {
    return new TrayMenuItem(null, null, false);
  }

  /** Whether this entry is a separator. */
  public boolean isSeparator() {
    return this.label == null;
  }
}
