package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.AbstractNotification;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * One notification that the center took, by the request identifier that the notifier gave it. It
 * ends when the user clicks it, picks a button, or dismisses it, or on {@link #close()}.
 */
public class MacNotification extends AbstractNotification {
  private final MacNotifier notifier;
  private final String identifier;

  MacNotification(
      MacNotifier notifier,
      String identifier,
      Notification notification,
      Path image,
      Consumer<NotificationHandle> closed) {
    super(notification, image, closed);
    this.notifier = notifier;
    this.identifier = identifier;
  }

  /** The identifier of the request. */
  String identifier() {
    return this.identifier;
  }

  @Override
  protected void withdraw() {
    this.notifier.withdraw(this);
  }

  @Override
  protected void release() {
    this.notifier.forget(this.identifier);
  }

  /**
   * The user answered the notification with {@code action}, a key of {@link AbstractNotification},
   * or nothing for a dismissal. macOS takes the notification away after either.
   */
  void answered(String action) {
    this.respond(action);
    this.markClosed();
  }

  /**
   * Delivers what the center sends for the action {@code action}: {@code
   * com.apple.UNNotificationDefaultActionIdentifier} for a click, {@code action-N} for button N, or
   * {@code com.apple.UNNotificationDismissActionIdentifier}. For tests: no test can click a
   * notification.
   */
  void simulateResponse(String action) {
    MacNotifier.responded(this.identifier, action);
  }
}
