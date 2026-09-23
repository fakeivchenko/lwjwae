package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.AbstractNotification;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * One notification that the server took, by the ID that it returned. The server reports its end
 * with {@code NotificationClosed}, apart from a click, which arrives as {@code ActionInvoked}.
 */
public class GtkNotification extends AbstractNotification {
  private final GtkNotifier notifier;
  private final int id;

  GtkNotification(
      GtkNotifier notifier,
      int id,
      Notification notification,
      Path image,
      Consumer<NotificationHandle> closed) {
    super(notification, image, closed);
    this.notifier = notifier;
    this.id = id;
  }

  /** The ID that the server gave the notification. */
  int id() {
    return this.id;
  }

  @Override
  protected void withdraw() {
    this.notifier.withdraw(this);
  }

  /** The server reported {@code ActionInvoked} with {@code action}. */
  void invoked(String action) {
    this.respond(action);
  }

  /** The server reported {@code NotificationClosed}. */
  void closedByServer() {
    this.markClosed();
  }

  /**
   * Delivers what the server sends for a click on the button with {@code key}: {@code default} for
   * the notification itself, {@code action-N} for button N. For tests: no test can click a
   * notification.
   */
  void simulateAction(String key) {
    this.notifier.actionInvoked(this.id, key);
  }

  /** Delivers what the server sends when the user dismisses the notification. For tests. */
  void simulateDismiss() {
    this.notifier.notificationClosed(this.id);
  }
}
