package dev.ivchenko.lwjwae.notification;

import java.util.Objects;

/**
 * One button of a notification.
 *
 * <p>The action runs off the UI thread, so it may block or call back into a window. Most desktops
 * take the notification away once the user picks a button.
 *
 * @param label The text of the button.
 * @param action What happens when the user picks the button. A {@code null} action makes the button
 *     do nothing.
 */
public record NotificationAction(String label, Runnable action) {
  public NotificationAction {
    Objects.requireNonNull(label, "label");
  }
}
