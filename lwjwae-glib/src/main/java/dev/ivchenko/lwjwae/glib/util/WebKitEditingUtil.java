package dev.ivchenko.lwjwae.glib.util;

import dev.ivchenko.lwjwae.menu.MenuRole;
import lombok.experimental.UtilityClass;

/** The editing commands of WebKitGTK that the editing roles of a menu run. */
@UtilityClass
public class WebKitEditingUtil {
  /**
   * The name that {@code webkit_web_view_execute_editing_command} takes for {@code role}.
   *
   * @throws IllegalArgumentException If the role doesn't edit the page.
   */
  public String command(MenuRole role) {
    return switch (role) {
      case UNDO -> "Undo";
      case REDO -> "Redo";
      case CUT -> "Cut";
      case COPY -> "Copy";
      case PASTE -> "Paste";
      case SELECT_ALL -> "SelectAll";
      default -> throw new IllegalArgumentException("Not an editing role: " + role);
    };
  }
}
