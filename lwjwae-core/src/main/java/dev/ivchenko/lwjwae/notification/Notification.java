package dev.ivchenko.lwjwae.notification;

import java.util.List;
import lombok.Builder;

/**
 * What a desktop notification says and offers.
 *
 * <p>The desktop draws the notification in its own style, and each one supports a different part of
 * it: every desktop shows the title, nearly every one the body, most the image and the buttons. A
 * notification that states everything in its title and body reads well everywhere. The image is PNG
 * for the reason that the tray icon is: it's the one format that every platform reads as it is.
 *
 * <pre>{@code
 * Notification.builder()
 *     .title("Export finished")
 *     .body("report.pdf, 12 pages")
 *     .actions(List.of(new NotificationAction("Open folder", this::openFolder)))
 *     .onActivate(window::show)
 *     .build()
 * }</pre>
 *
 * @param title The first line, in bold on most desktops. Required.
 * @param body The text under the title. Default: none.
 * @param icon The PNG bytes of an image to show next to the text. Default: none.
 * @param actions The buttons, in order. Desktops show a few at most, often two or three, and some
 *     none. Default: none.
 * @param onActivate What happens when the user clicks the notification itself rather than a button,
 *     where the desktop reports it. Default: nothing.
 */
@Builder(toBuilder = true)
public record Notification(
    String title, String body, byte[] icon, List<NotificationAction> actions, Runnable onActivate) {
  public Notification {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("A notification needs a title");
    }
    if (icon != null && icon.length == 0) {
      icon = null;
    }
    if (actions == null) {
      actions = List.of();
    }
    actions = List.copyOf(actions);
  }
}
