package dev.ivchenko.lwjwae.dialog;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The dialogs of the platform over one window: files to open, a file to save, and messages. {@link
 * dev.ivchenko.lwjwae.Window#dialogs()} hands them out.
 *
 * <p>A dialog is modal to its window, and a method returns without waiting for the user: the future
 * completes with the answer, and canceling it closes the dialog. A dialog that is up when its
 * window closes is canceled with it, since a dialog must not outlive the window that it belongs to.
 *
 * <p>Every method is safe to call from any thread, and throws {@link IllegalStateException} once
 * the window is closed.
 */
public interface Dialogs {
  /**
   * Shows the dialog of the platform that opens files, or folders, over the window, and returns
   * without waiting for the user.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The common item dialog, modal to the window; the kinds of file are the filters
   *       of the dialog.
   *   <li>macOS: A sheet of the window. The title shows above the files, since a sheet has no title
   *       bar, and the kinds of file merge into one list of extensions, since a panel has no menu
   *       of them.
   *   <li>Linux, GTK 3: {@code GtkFileChooserNative} through the desktop portal wherever the
   *       session has one, so the dialog is the one of the desktop, such as Dolphin's on KDE
   *       Plasma, and the dialog of GTK without a portal. {@code GTK_USE_PORTAL=0} keeps the one of
   *       GTK.
   *   <li>Linux, GTK 4: As on GTK 3; {@code GDK_DEBUG=no-portals} keeps the one of GTK.
   * </ul>
   *
   * @return The files or folders that the user picked, or none if they canceled. Canceling the
   *     future closes the dialog.
   */
  CompletableFuture<List<Path>> open(OpenDialogParameters parameters);

  /**
   * Shows the dialog of the platform that saves a file over the window, and returns without waiting
   * for the user. The dialog asks before it picks a file that exists; nothing is written.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: The common item dialog, modal to the window.
   *   <li>macOS: A sheet of the window, with the title above the name, as for opening.
   *   <li>Linux, GTK 3: {@code GtkFileChooserNative}, through the desktop portal, as for opening.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @return The file that the user picked, or empty if they canceled. Canceling the future closes
   *     the dialog.
   */
  CompletableFuture<Optional<Path>> save(SaveDialogParameters parameters);

  /**
   * Shows a message over the window, and returns without waiting for the user.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: A message box, modal to the window. The detail follows the message after a blank
   *       line, and without a title the title bar shows the title of the window.
   *   <li>macOS: An alert sheet of the window. The message is in bold and the detail under it; the
   *       title isn't shown, since a sheet has no title bar.
   *   <li>Linux, GTK 3: A {@code GtkMessageDialog}, with the detail as its secondary text and the
   *       buttons in the language of the user, from the translations of GTK.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @return {@code true} if the user chose OK or yes, {@code false} for cancel, no, or a closed
   *     dialog. Canceling the future closes the dialog.
   */
  CompletableFuture<Boolean> message(MessageDialogParameters parameters);

  /**
   * Shows {@code message} with an OK button, the way {@code alert} does on a page, and returns
   * without waiting for the user. The future completes when the user closed the message.
   */
  default CompletableFuture<Void> alert(String message) {
    return Dialogs.following(this.message(MessageDialogParameters.of(message)), _ -> (Void) null);
  }

  /**
   * Asks {@code message} with OK and Cancel, the way {@code confirm} does on a page, and returns
   * without waiting for the user.
   *
   * @return {@code true} for OK. Canceling the future closes the dialog.
   */
  default CompletableFuture<Boolean> confirm(String message) {
    return this.message(
        MessageDialogParameters.builder()
            .message(message)
            .level(MessageLevel.QUESTION)
            .buttons(MessageButtons.OK_CANCEL)
            .build());
  }

  /**
   * Lets the user pick one file to open, of one of {@code types}, or of any type without them.
   *
   * @return The file, or empty if the user canceled. Canceling the future closes the dialog.
   */
  default CompletableFuture<Optional<Path>> pickFile(FileType... types) {
    return Dialogs.following(
        this.open(OpenDialogParameters.builder().fileTypes(List.of(types)).build()),
        files -> files.stream().findFirst());
  }

  /**
   * Lets the user pick files to open, of {@code types}, or of any type without them.
   *
   * @return The files, or none if the user canceled. Canceling the future closes the dialog.
   */
  default CompletableFuture<List<Path>> pickFiles(FileType... types) {
    return this.open(
        OpenDialogParameters.builder().fileTypes(List.of(types)).multiple(true).build());
  }

  /**
   * Lets the user pick a folder.
   *
   * @return The folder, or empty if the user canceled. Canceling the future closes the dialog.
   */
  default CompletableFuture<Optional<Path>> pickFolder() {
    return Dialogs.following(
        this.open(OpenDialogParameters.builder().directories(true).build()),
        folders -> folders.stream().findFirst());
  }

  /**
   * Lets the user pick where to save a file, suggesting {@code fileName}, of one of {@code types}.
   *
   * @return The file, or empty if the user canceled. Canceling the future closes the dialog.
   */
  default CompletableFuture<Optional<Path>> pickSaveFile(String fileName, FileType... types) {
    return this.save(
        SaveDialogParameters.builder().fileName(fileName).fileTypes(List.of(types)).build());
  }

  /**
   * {@code dialog} with its answer turned by {@code answer}, where canceling the result cancels
   * {@code dialog} too, which closes it, as canceling {@code dialog} itself does.
   */
  private static <T, R> CompletableFuture<R> following(
      CompletableFuture<T> dialog, Function<T, R> answer) {
    CompletableFuture<R> result = dialog.thenApply(answer);
    result.whenComplete(
        (_, _) -> {
          if (result.isCancelled()) {
            dialog.cancel(true);
          }
        });
    return result;
  }
}
