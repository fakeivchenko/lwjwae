package dev.ivchenko.lwjwae.glib.util;

import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.shortcut.ShortcutKey;
import dev.ivchenko.lwjwae.shortcut.ShortcutModifier;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * The names of the X keysyms of the keys of a shortcut, which both an X11 grab and the {@code
 * GlobalShortcuts} portal take, and the accelerators of GTK made of them.
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

  /**
   * {@code shortcut} as GTK writes an accelerator, which {@code gtk_accelerator_parse} of GTK 3 and
   * the shortcut triggers of GTK 4 read: {@code <Control><Shift>s}.
   */
  public String accelerator(Shortcut shortcut) {
    StringBuilder text = new StringBuilder();
    if (shortcut.has(ShortcutModifier.CONTROL)) {
      text.append("<Control>");
    }
    if (shortcut.has(ShortcutModifier.ALT)) {
      text.append("<Alt>");
    }
    if (shortcut.has(ShortcutModifier.SHIFT)) {
      text.append("<Shift>");
    }
    if (shortcut.has(ShortcutModifier.META)) {
      text.append("<Super>");
    }
    return text.append(KeysymUtil.name(shortcut.key())).toString();
  }
}
