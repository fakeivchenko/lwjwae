package dev.ivchenko.lwjwae.glib.util;

import dev.ivchenko.lwjwae.shortcut.ShortcutKey;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * The names of the X keysyms of the keys of a shortcut, which both an X11 grab and the {@code
 * GlobalShortcuts} portal take.
 */
@UtilityClass
public class KeysymUtil {
  /** The keysym name of {@code key}: {@code a}, {@code 7}, {@code F5}, {@code Page_Up}. */
  public String name(ShortcutKey key) {
    if (key.label().length() == 1) {
      return key.label().toLowerCase(Locale.ROOT);
    }
    return switch (key) {
      case SPACE -> "space";
      case ENTER -> "Return";
      case BACKSPACE -> "BackSpace";
      case PAGE_UP -> "Page_Up";
      case PAGE_DOWN -> "Page_Down";
      default -> key.label();
    };
  }
}
