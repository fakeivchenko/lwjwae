package dev.ivchenko.lwjwae.notification;

/**
 * A notification that the desktop took, and the handle to take it back.
 *
 * <p>A notification goes away on its own: when it expires, when the user dismisses it, or when the
 * user picks one of its buttons. {@link #isClosed()} turns true then, where the desktop reports it.
 * The application takes back its notifications when it closes, because their handlers go with it
 * and a button that does nothing is worse than no notification.
 *
 * <p>The handle is not {@link AutoCloseable} on purpose: a notification is meant to outlive the
 * code that shows it, and closing it at the end of a block would take it away before anyone reads
 * it.
 */
public interface NotificationHandle {
  /** Whether the notification is gone: closed here, dismissed, expired, or answered. */
  boolean isClosed();

  /** Takes the notification off the screen, if it's still there. This method is idempotent. */
  void close();
}
