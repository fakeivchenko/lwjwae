package dev.ivchenko.lwjwae.shortcut;

/** A key that a {@link Shortcut} holds down with its key. */
public enum ShortcutModifier {
  /** {@code Ctrl}, and {@code Control} on macOS. */
  CONTROL("Ctrl"),

  /** {@code Alt}, and {@code Option} on macOS. */
  ALT("Alt"),

  /** {@code Shift}. */
  SHIFT("Shift"),

  /** The logo key: {@code Win} on Windows, {@code Command} on macOS, {@code Super} on Linux. */
  META("Meta");

  private final String label;

  ShortcutModifier(String label) {
    this.label = label;
  }

  /** The name of the modifier in a shortcut, for example {@code Ctrl}. */
  public String label() {
    return this.label;
  }
}
