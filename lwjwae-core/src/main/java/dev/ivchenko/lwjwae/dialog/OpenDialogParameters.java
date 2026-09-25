package dev.ivchenko.lwjwae.dialog;

import java.nio.file.Path;
import java.util.List;
import lombok.Builder;

/**
 * What a dialog that opens files or folders asks for, see {@link
 * dev.ivchenko.lwjwae.Window#showOpenDialog}.
 *
 * @param title The title of the dialog, or {@code null} for the one of the platform.
 * @param directory The folder that the dialog starts in, or {@code null} for the one that the
 *     platform remembers.
 * @param fileTypes The kinds of file that the dialog offers, the first one shown first. None, the
 *     default, shows every file.
 * @param multiple Whether the user can pick more than one. Default: {@code false}.
 * @param directories Whether the user picks folders rather than files. Default: {@code false}.
 */
@Builder(toBuilder = true)
public record OpenDialogParameters(
    String title, Path directory, List<FileType> fileTypes, boolean multiple, boolean directories) {
  public OpenDialogParameters {
    if (fileTypes == null) {
      fileTypes = List.of();
    }
  }

  /** A dialog that opens one file of any kind. */
  public static OpenDialogParameters createDefault() {
    return builder().build();
  }
}
