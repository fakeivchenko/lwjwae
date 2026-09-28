package dev.ivchenko.lwjwae.glib.util;

import dev.ivchenko.lwjwae.dialog.FileType;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The glob patterns of a {@link FileType} for a {@code GtkFileFilter}.
 *
 * <p>GTK matches a pattern with case, and the suffixes of GTK 4 that don't are missing from GTK 3
 * and the portal, so every letter becomes a bracket of both cases: {@code png} is {@code
 * *.[pP][nN][gG]}, which finds {@code photo.PNG} too.
 */
@UtilityClass
public class FileTypeUtil {
  /** The patterns of {@code type}: one per extension, or {@code *} for a type without any. */
  public List<String> patterns(FileType type) {
    if (type.extensions().isEmpty()) {
      return List.of("*");
    }
    return type.extensions().stream().map(FileTypeUtil::pattern).toList();
  }

  private String pattern(String extension) {
    StringBuilder pattern = new StringBuilder("*.");
    extension
        .codePoints()
        .forEach(
            character -> {
              int upper = Character.toUpperCase(character);
              int lower = Character.toLowerCase(character);
              if (upper == lower) {
                pattern.appendCodePoint(character);
              } else {
                pattern.append('[').appendCodePoint(lower).appendCodePoint(upper).append(']');
              }
            });
    return pattern.toString();
  }
}
