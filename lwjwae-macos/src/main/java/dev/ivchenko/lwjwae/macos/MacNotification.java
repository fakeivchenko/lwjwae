package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One notification that the center took, by the request identifier that the notifier gave it.
 *
 * <p>It ends when the user clicks it, picks a button, or dismisses it, or on {@link #close()};
 * whichever comes first ends it once, deletes its image, and tells the application.
 */
public class MacNotification implements NotificationHandle {
  private final MacNotifier notifier;
  private final String identifier;
  private final Notification notification;
  private final Path image;
  private final Consumer<NotificationHandle> closedCallback;
  private final AtomicBoolean closed = new AtomicBoolean();

  MacNotification(
      MacNotifier notifier,
      String identifier,
      Notification notification,
      Path image,
      Consumer<NotificationHandle> closed) {
    this.notifier = notifier;
    this.identifier = identifier;
    this.notification = notification;
    this.image = image;
    this.closedCallback = closed;
  }

  /** The identifier of the request. */
  String identifier() {
    return this.identifier;
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

  /** The center reported a response: the notification is gone. */
  void markClosed() {
    if (this.closed.compareAndSet(false, true)) {
      this.notifier.forget(this.identifier);
      this.finish();
    }
  }

  /** The user clicked the notification itself. */
  void activate() {
    runOffTheMainThread(this.notification.onActivate());
  }

  /** The user picked button {@code index}. */
  void pick(int index) {
    List<NotificationAction> actions = this.notification.actions();
    if (index >= 0 && index < actions.size()) {
      runOffTheMainThread(actions.get(index).action());
    }
  }

  /**
   * Delivers what the center sends for the action {@code action}: {@code
   * com.apple.UNNotificationDefaultActionIdentifier} for a click, {@code action-N} for button N, or
   * {@code com.apple.UNNotificationDismissActionIdentifier}. For tests: no test can click a
   * notification.
   */
  void simulateResponse(String action) {
    this.notifier.responded(this.identifier, action);
  }

  private void finish() {
    deleteQuietly(this.image);
    this.closedCallback.accept(this);
  }

  /** Deletes a file, or an empty directory. A failure leaves a file in the temporary directory. */
  static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      ThrowableUtil.report(e);
    }
  }

  /** Runs a handler of the user on a virtual thread, so it may block or call back into a window. */
  private static void runOffTheMainThread(Runnable action) {
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
