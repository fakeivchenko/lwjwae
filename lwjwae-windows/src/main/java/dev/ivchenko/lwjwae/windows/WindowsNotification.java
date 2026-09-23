package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.AbstractNotification;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.Toasts;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * One toast, and the reference to its {@code ToastNotification} that takes it back.
 *
 * <p>A toast ends when the user clicks it or one of its buttons, when the user dismisses it, when
 * it fails to show, or on {@link #close()}. A toast that times out is not over: it moves to
 * Notification Center, where the user can still read and click it, so the handle stays open. That
 * is also where every toast goes under Do Not Disturb, which reports it as timed out at once;
 * taking such a toast back would hide every notification from a user who only asked for quiet.
 */
public class WindowsNotification extends AbstractNotification {
  private final WindowsNotifier notifier;
  private final MemorySegment toast;

  WindowsNotification(
      WindowsNotifier notifier,
      MemorySegment toast,
      Notification notification,
      Path image,
      Consumer<NotificationHandle> closed) {
    super(notification, image, closed);
    this.notifier = notifier;
    this.toast = toast;
  }

  /** The {@code ToastNotification}, borrowed. */
  MemorySegment toast() {
    return this.toast;
  }

  @Override
  protected void withdraw() {
    this.notifier.withdraw(this);
  }

  @Override
  protected void release() {
    Com.release(this.toast);
  }

  /**
   * The user clicked the toast or a button: {@code arguments} is the action key. Windows takes the
   * toast away after a click.
   */
  void activated(String arguments) {
    this.respond(arguments);
    this.markClosed();
  }

  /** Windows took the toast off the screen, for {@code reason}. */
  void dismissed(int reason) {
    if (reason != Toasts.DISMISSED_BY_TIMEOUT) {
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

  /** Delivers what Windows reports when the toast times out. For tests. */
  void simulateTimeout() {
    this.dismissed(Toasts.DISMISSED_BY_TIMEOUT);
  }
}
