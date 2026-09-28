package dev.ivchenko.lwjwae.dialog;

import lombok.Builder;

/**
 * What a message dialog says and offers, see {@link dev.ivchenko.lwjwae.Window#showMessageDialog}.
 *
 * <p>Platforms:
 *
 * <ul>
 *   <li>Windows: The level picks the icon of the message box; the detail follows the message after
 *       a blank line; without a title, the title of the window shows.
 *   <li>macOS: The title isn't shown: the message is a sheet of the window, which has no title bar.
 *       The message is in bold and the detail under it.
 *   <li>Linux, GTK 3: The message is the primary text and the detail the secondary one. The buttons
 *       are in the language of the user, from the translations of GTK.
 *   <li>Linux, GTK 4: As on GTK 3.
 * </ul>
 *
 * @param title The title of the dialog window, or {@code null} for none.
 * @param message The message, in the bold first line where the platform has one. Default: empty.
 * @param detail More text under the message, or {@code null} for none.
 * @param level How much the message matters. Default: {@link MessageLevel#INFO}.
 * @param buttons The buttons. Default: {@link MessageButtons#OK}.
 */
@Builder(toBuilder = true)
public record MessageDialogParameters(
    String title, String message, String detail, MessageLevel level, MessageButtons buttons) {
  public MessageDialogParameters {
    if (message == null) {
      message = "";
    }
    if (level == null) {
      level = MessageLevel.INFO;
    }
    if (buttons == null) {
      buttons = MessageButtons.OK;
    }
  }

  /** A dialog with {@code message} and one OK button. */
  public static MessageDialogParameters of(String message) {
    return builder().message(message).build();
  }
}
