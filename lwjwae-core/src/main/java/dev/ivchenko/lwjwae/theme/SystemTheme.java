package dev.ivchenko.lwjwae.theme;

import java.util.Locale;

/**
 * The colors that the user chose for the desktop, see {@link
 * dev.ivchenko.lwjwae.Application#theme}.
 */
public enum SystemTheme {
  /** Dark text on a light background. Also the answer where the desktop has no choice. */
  LIGHT,

  /** Light text on a dark background. */
  DARK;

  /**
   * The name that a page sees: {@code light} or {@code dark}, as in {@code prefers-color-scheme}.
   */
  public String pageName() {
    return this.name().toLowerCase(Locale.ROOT);
  }
}
