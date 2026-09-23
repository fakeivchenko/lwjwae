package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.Toasts;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One toast, and the reference to its {@code ToastNotification} that takes it back.
 *
 * <p>A toast ends when the user clicks it or one of its buttons, when the user dismisses it, when
 * it fails to show, or on {@link #close()}. A toast that times out is not over: it moves to the
 * notification center, where the user can still click it, so the handle stays open. Whichever way
 * it ends, it ends once: the handle releases the toast, deletes the image, and tells the
 * application.
 */
public class WindowsNotification implements NotificationHandle {
  private final WindowsNotifier notifier;
  private final MemorySegment toast;
  private final Notification notification;
  private final Path image;
  private final Consumer<NotificationHandle> closedCallback;
  private final AtomicBoolean closed = new AtomicBoolean();

  WindowsNotification(
      WindowsNotifier notifier,
      MemorySegment toast,
      Notification notification,
      Path image,
      Consumer<NotificationHandle> closed) {
    this.notifier = notifier;
    this.toast = toast;
    this.notification = notification;
    this.image = image;
    this.closedCallback = closed;
  }

  /** The {@code ToastNotification}, borrowed. */
  MemorySegment toast() {
    return this.toast;
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

  /**
   * The user clicked the toast or a button: {@code arguments} is {@code default} for the toast, or
   * {@code action-N} for button N. Windows takes the toast away after a click.
   */
  void activated(String arguments) {
    if (this.closed.get()) {
      return;
    }
    int button = WindowsNotifier.buttonOf(arguments);
    List<NotificationAction> actions = this.notification.actions();
    if (button >= 0 && button < actions.size()) {
      runOffTheUiThread(actions.get(button).action());
    } else if (button < 0) {
      runOffTheUiThread(this.notification.onActivate());
    }
    this.markClosed();
  }

  /** Windows took the toast off the screen, for {@code reason}. */
  void dismissed(int reason) {
    if (reason == Toasts.DISMISSED_BY_USER || reason == Toasts.DISMISSED_BY_APPLICATION) {
      this.markClosed();
    }
  }

  /** Windows could not show the toast. */
  void failed() {
    this.markClosed();
  }

  /** Delivers what Windows reports for a click with {@code arguments}. For tests. */
  void simulateActivation(String arguments) {
    this.activated(arguments);
  }

  /** Delivers what Windows reports when the user dismisses the toast. For tests. */
  void simulateDismiss() {
    this.dismissed(Toasts.DISMISSED_BY_USER);
  }

  private void markClosed() {
    if (this.closed.compareAndSet(false, true)) {
      this.finish();
    }
  }

  private void finish() {
    Com.release(this.toast);
    WindowsNotifier.deleteQuietly(this.image);
    this.closedCallback.accept(this);
  }

  /** Runs a handler of the user on a virtual thread, so it may block or call back into a window. */
  private static void runOffTheUiThread(Runnable action) {
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
