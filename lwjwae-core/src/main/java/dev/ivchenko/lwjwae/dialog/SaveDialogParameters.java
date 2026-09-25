package dev.ivchenko.lwjwae.dialog;

import java.nio.file.Path;
import java.util.List;
import lombok.Builder;

/**
 * What a dialog that saves a file asks for, see {@link dev.ivchenko.lwjwae.Window#showSaveDialog}.
 * The dialog asks the user before it picks a file that exists.
 *
 * @param title The title of the dialog, or {@code null} for the one of the platform.
 * @param directory The folder that the dialog starts in, or {@code null} for the one that the
 *     platform remembers.
 * @param fileName The name that the dialog proposes, or {@code null} for none.
 * @param fileTypes The kinds of file that the dialog offers. None, the default, offers any.
 */
@Builder(toBuilder = true)
public record SaveDialogParameters(
    String title, Path directory, String fileName, List<FileType> fileTypes) {
  public SaveDialogParameters {
    if (fileTypes == null) {
      fileTypes = List.of();
    }
  }

  /** A dialog that saves a file of any kind, with no name proposed. */
  public static SaveDialogParameters createDefault() {
    return builder().build();
  }
}
