package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.notification.Notifications;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The notifications of one application, and the ones of them that are up, which go when it quits.
 *
 * <p>A notification can go away on its own before {@link #show} has added it, and the application
 * can quit while it is shown; both are settled after the backend returns the handle, so that no
 * notification outlives the application and none stays in the set once it's gone.
 */
final class ApplicationNotifications implements Notifications {
  private final AbstractApplication application;
  private final Set<NotificationHandle> shown = ConcurrentHashMap.newKeySet();

  ApplicationNotifications(AbstractApplication application) {
    this.application = application;
  }

  @Override
  public NotificationHandle show(Notification notification) {
    Objects.requireNonNull(notification, "notification");
    this.application.checkOpen();
    NotificationHandle handle =
        this.application.createNotification(notification, this.shown::remove);
    this.shown.add(handle);
    // Gone before it was added: its callback found nothing to remove, so drop it here.
    if (handle.isClosed()) {
      this.shown.remove(handle);
    }
    // Shown while the application was taking the others back: this one missed it, so take it back.
    if (this.application.isClosed()) {
      handle.close();
      throw new IllegalStateException("The application is closed");
    }
    return handle;
  }

  /** Takes back every notification that is up. The application calls this as it quits. */
  void closeAll() {
    for (NotificationHandle notification : List.copyOf(this.shown)) {
      try {
        notification.close();
      } catch (Throwable t) {
        ThrowableUtil.report(t);
      }
    }
  }
}
