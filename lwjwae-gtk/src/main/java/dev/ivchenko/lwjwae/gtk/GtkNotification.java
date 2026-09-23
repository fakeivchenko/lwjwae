package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One notification that the server took, by the ID that it returned.
 *
 * <p>It ends one of two ways: {@link #close()} takes it back, or the server reports it gone. Either
 * way ends it once, deletes its image, and tells the application.
 */
public class GtkNotification implements NotificationHandle {
  private final GtkNotifier notifier;
  private final int id;
  private final Notification notification;
  private final Path image;
  private final Consumer<NotificationHandle> closedCallback;
  private final AtomicBoolean closed = new AtomicBoolean();

  GtkNotification(
      GtkNotifier notifier,
      int id,
      Notification notification,
      Path image,
      Consumer<NotificationHandle> closed) {
    this.notifier = notifier;
    this.id = id;
    this.notification = notification;
    this.image = image;
    this.closedCallback = closed;
  }

  /** The ID that the server gave the notification. */
  int id() {
    return this.id;
  }

  @Override
  public boolean isClosed() {
    return this.closed.get();
  }

  @Override
  public void close() {
    if (this.closed.compareAndSet(false, true)) {
      this.notifier.withdraw(this);
      this.finish();
    }
  }

  /** The server reported the notification gone: expired, dismissed, or answered. */
  void markClosed() {
    if (this.closed.compareAndSet(false, true)) {
      this.finish();
    }
  }

  /** The user clicked the notification itself. */
  void activate() {
    runOffTheGtkThread(this.notification.onActivate());
  }

  /** The user picked button {@code index}. */
  void pick(int index) {
    List<NotificationAction> actions = this.notification.actions();
    if (index >= 0 && index < actions.size()) {
      runOffTheGtkThread(actions.get(index).action());
    }
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

  private void finish() {
    GtkNotifier.deleteQuietly(this.image);
    this.closedCallback.accept(this);
  }

  /** Runs a handler of the user on a virtual thread, so it may block or call back into a window. */
  private static void runOffTheGtkThread(Runnable action) {
    if (action == null) {
      return;
    }
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                action.run();
              } catch (Throwable t) {
                ThrowableUtil.report(t);
              }
            });
  }
}
