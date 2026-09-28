package dev.ivchenko.lwjwae.dialog;

import java.nio.file.Path;
import java.util.List;
import lombok.Builder;

/**
 * What a dialog that opens files or folders asks for, see {@link
 * dev.ivchenko.lwjwae.Window#showOpenDialog}.
 *
 * <p>Platforms:
 *
 * <ul>
 *   <li>Windows: The kinds of file are the filters of the dialog, the first one selected.
 *   <li>macOS: The title shows above the files, since the dialog is a sheet of the window without a
 *       title bar. The kinds of file merge into one list of extensions, since a panel has no menu
 *       of them.
 *   <li>Linux, GTK 3: Each kind of file is a filter; an extension matches in either case. Inside a
 *       sandbox, the desktop portal shows the dialog.
 *   <li>Linux, GTK 4: As on GTK 3.
 * </ul>
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
