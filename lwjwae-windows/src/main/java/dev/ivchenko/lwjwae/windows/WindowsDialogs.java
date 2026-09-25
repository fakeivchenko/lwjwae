package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.dialog.DialogCompletion;
import dev.ivchenko.lwjwae.dialog.FileType;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.FileDialog;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

/**
 * The dialogs of a {@link WindowsWindow}: the common item dialogs for files and folders, and {@code
 * MessageBoxW} for messages.
 *
 * <p>Both are modal: they run a loop of their own on the UI thread until the user answers, and the
 * call that shows them returns only then. The loop dispatches the messages of windows, which is how
 * the work that other threads queue keeps running meanwhile, see {@link WindowsDispatcher}, and how
 * a cancellation reaches the dialog. A message box has no {@code TaskDialog} here: that one needs
 * version 6 of the common controls, which a Java launcher doesn't declare in its manifest.
 */
@UtilityClass
class WindowsDialogs {
  /** Shows the dialog that opens files or folders over {@code owner}, until the user answers. */
  void open(
      MemorySegment owner,
      OpenDialogParameters parameters,
      DialogCompletion<List<Path>> completion) {
    MemorySegment dialog = FileDialog.newOpenDialog();
    int options = FileDialog.FOS_FORCEFILESYSTEM | FileDialog.FOS_PATHMUSTEXIST;
    if (parameters.multiple()) {
      options |= FileDialog.FOS_ALLOWMULTISELECT;
    }
    if (parameters.directories()) {
      options |= FileDialog.FOS_PICKFOLDERS;
    } else {
      options |= FileDialog.FOS_FILEMUSTEXIST;
    }
    WindowsDialogs.show(
        dialog,
        owner,
        options,
        parameters.title(),
        parameters.directory(),
        parameters.directories() ? List.of() : parameters.fileTypes(),
        completion,
        picked ->
            picked ? FileDialog.results(dialog).stream().map(Path::of).toList() : List.<Path>of());
  }

  /** Shows the dialog that saves a file over {@code owner}, until the user answers. */
  void save(
      MemorySegment owner,
      SaveDialogParameters parameters,
      DialogCompletion<Optional<Path>> completion) {
    MemorySegment dialog = FileDialog.newSaveDialog();
    if (parameters.fileName() != null) {
      FileDialog.setFileName(dialog, parameters.fileName());
    }
    WindowsDialogs.show(
        dialog,
        owner,
        FileDialog.FOS_FORCEFILESYSTEM
            | FileDialog.FOS_PATHMUSTEXIST
            | FileDialog.FOS_OVERWRITEPROMPT,
        parameters.title(),
        parameters.directory(),
        parameters.fileTypes(),
        completion,
        picked -> picked ? Optional.of(Path.of(FileDialog.result(dialog))) : Optional.empty());
  }

  /**
   * Sets a file dialog up, shows it, and answers {@code completion} through {@code answer}, which
   * learns whether the user picked anything. A cancellation that comes after {@code Show} returned
   * finds the dialog gone and leaves the window alone.
   */
  private <T> void show(
      MemorySegment dialog,
      MemorySegment owner,
      int options,
      String title,
      Path directory,
      List<FileType> fileTypes,
      DialogCompletion<T> completion,
      Function<Boolean, T> answer) {
    AtomicBoolean shown = new AtomicBoolean(true);
    try {
      FileDialog.addOptions(dialog, options);
      if (title != null) {
        FileDialog.setTitle(dialog, title);
      }
      if (directory != null) {
        FileDialog.setFolder(dialog, directory.toAbsolutePath().toString());
      }
      if (!fileTypes.isEmpty()) {
        FileDialog.setFileTypes(dialog, fileTypes);
      }
      completion.onCancel(
          () -> {
            if (shown.get()) {
              User32.cancelOwnedDialog(owner);
            }
          });
      int hresult = FileDialog.show(dialog, owner);
      if (hresult != FileDialog.CANCELLED) {
        Com.check("Show", hresult);
      }
      completion.complete(answer.apply(hresult != FileDialog.CANCELLED));
    } finally {
      shown.set(false);
      Com.release(dialog);
    }
  }

  /** Shows a message box over {@code owner}, until the user answers. */
  void message(
      MemorySegment owner,
      MessageDialogParameters parameters,
      DialogCompletion<Boolean> completion) {
    int buttons =
        switch (parameters.buttons()) {
          case OK -> User32.MB_OK;
          case OK_CANCEL -> User32.MB_OKCANCEL;
          case YES_NO -> User32.MB_YESNO;
        };
    int icon =
        switch (parameters.level()) {
          case INFO -> User32.MB_ICONINFORMATION;
          case WARNING -> User32.MB_ICONWARNING;
          case ERROR -> User32.MB_ICONERROR;
          case QUESTION -> User32.MB_ICONQUESTION;
        };
    int type = buttons | icon;
    String text =
        parameters.detail() == null
            ? parameters.message()
            : parameters.message() + "\n\n" + parameters.detail();
    // Without a caption, a message box says "Error"; the window's title fits better.
    String caption = parameters.title() != null ? parameters.title() : User32.title(owner);
    AtomicBoolean shown = new AtomicBoolean(true);
    completion.onCancel(
        () -> {
          if (shown.get()) {
            User32.endOwnedDialog(owner, User32.IDCANCEL);
          }
        });
    int pressed;
    try {
      pressed = User32.messageBox(owner, text, caption, type);
    } finally {
      shown.set(false);
    }
    completion.complete(pressed == User32.IDOK || pressed == User32.IDYES);
  }
}
