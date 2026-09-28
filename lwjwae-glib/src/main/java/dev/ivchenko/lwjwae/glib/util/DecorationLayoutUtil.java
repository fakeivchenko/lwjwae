package dev.ivchenko.lwjwae.glib.util;

import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Edits a GTK decoration layout, the {@code gtk-decoration-layout} setting that says which buttons
 * a title bar has and on which side, for example {@code "icon:minimize,maximize,close"}.
 *
 * <p>A window that may not be minimized or maximized keeps the layout of the desktop, left or
 * right, and loses only those buttons, so it still looks like its neighbors.
 */
@UtilityClass
public class DecorationLayoutUtil {
  /** What GTK falls back to when the desktop sets no layout. */
  private final String DEFAULT_LAYOUT = "menu:minimize,maximize,close";

  /**
   * {@code layout} without the minimize button, the maximize button, or both.
   *
   * @param layout The layout of the desktop, or {@code null} for the default of GTK.
   */
  public String without(String layout, boolean minimize, boolean maximize) {
    String source = layout == null ? DEFAULT_LAYOUT : layout;
    return Arrays.stream(source.split(":", -1))
        .map(
            side ->
                Arrays.stream(side.split(","))
                    .filter(button -> !(minimize && button.equals("minimize")))
                    .filter(button -> !(maximize && button.equals("maximize")))
                    .collect(Collectors.joining(",")))
        .collect(Collectors.joining(":"));
  }
}
