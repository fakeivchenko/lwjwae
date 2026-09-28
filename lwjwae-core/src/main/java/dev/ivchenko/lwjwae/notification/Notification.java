package dev.ivchenko.lwjwae.notification;

import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.util.ResourceUtil;
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
 *     .icon("app/export.png")
 *     .actions(new NotificationAction("Open folder", this::openFolder))
 *     .onActivate(window::show)
 *     .build()
 * }</pre>
 *
 * <p>Platforms:
 *
 * <ul>
 *   <li>Windows: A toast: the title and the body as two lines, the image as the logo, and the
 *       buttons under them.
 *   <li>macOS: The title and the body; the image isn't shown, since macOS shows the icon of the
 *       application. The buttons show when the user expands the notification.
 *   <li>Linux, GTK 3: Whatever the notification server shows: every one shows the title, nearly
 *       every one the body, most the image and the buttons.
 *   <li>Linux, GTK 4: As on GTK 3.
 * </ul>
 *
 * @param title The first line, in bold on most desktops. Required.
 * @param body The text under the title. Default: none.
 * @param icon The PNG bytes of an image to show next to the text, or a PNG among the resources of
 *     the application through the builder. Default: none.
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

  /** A notification with a title and a body, and nothing else. */
  public static Notification of(String title, String body) {
    return Notification.builder().title(title).body(body).build();
  }

  /**
   * The builder, which also takes the image from the resources of the application and the buttons
   * as separate entries. Lombok leaves out a method whose name is already here, so the plain ones
   * are here too.
   */
  public static class NotificationBuilder {
    /** The PNG bytes of the image. */
    public NotificationBuilder icon(byte[] icon) {
      this.icon = icon;
      return this;
    }

    /**
     * A PNG among the resources of the application, such as {@code "app/export.png"}.
     *
     * @throws ResourceNotFoundException If the classpath has no such resource.
     */
    public NotificationBuilder icon(String resource) {
      return this.icon(ResourceUtil.read(resource));
    }

    /** The buttons, in order. */
    public NotificationBuilder actions(List<NotificationAction> actions) {
      this.actions = actions;
      return this;
    }

    /** The same as {@link #actions(List)}. */
    public NotificationBuilder actions(NotificationAction... actions) {
      return this.actions(List.of(actions));
    }
  }
}
