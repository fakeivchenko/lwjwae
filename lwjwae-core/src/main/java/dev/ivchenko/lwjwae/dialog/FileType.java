package dev.ivchenko.lwjwae.dialog;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A kind of file that a file dialog offers to show, such as images: a name for the list of the
 * dialog and the extensions that make a file one.
 *
 * @param name What the dialog calls the kind, for example {@code "Images"}.
 * @param extensions The extensions without the dot, for example {@code png}, matched without regard
 *     to case. None means every file.
 */
public record FileType(String name, List<String> extensions) {
  public FileType {
    Objects.requireNonNull(name, "name");
    extensions =
        extensions == null
            ? List.of()
            : extensions.stream()
                .map(extension -> extension.startsWith(".") ? extension.substring(1) : extension)
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .toList();
  }

  /** A kind of file with the given extensions, for example {@code of("Images", "png", "jpg")}. */
  public static FileType of(String name, String... extensions) {
    return new FileType(name, List.of(extensions));
  }
}
