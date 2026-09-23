package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.util.HandlerUtil;
import dev.ivchenko.lwjwae.util.TemporaryImages;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The part of a notification handle that doesn't depend on the platform: the end of it, which
 * happens once, and the routing of a click to the handler it belongs to.
 *
 * <p>A backend names each click with an action key: {@link #DEFAULT_ACTION} for the notification
 * itself and {@link #buttonAction(int)} for a button, and passes the key that the desktop reports
 * back to {@link #respond(String)}. A notification ends either way: {@link #close()} takes it back
 * through {@link #withdraw()}, or the backend learns that the desktop let it go and calls {@link
 * #markClosed()}. Whichever comes first ends it: {@link #release()} frees what the backend holds,
 * the image file is deleted, and the application stops tracking the handle.
 */
public abstract class AbstractNotification implements NotificationHandle {
  /** The action key of a click on the notification itself. */
  public static final String DEFAULT_ACTION = "default";

  private static final String BUTTON_ACTION_PREFIX = "action-";

  private final Notification notification;
  private final Path image;
  private final Consumer<NotificationHandle> closedCallback;
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Starts the handle of a notification that the desktop took.
   *
   * @param notification What the notification says and offers.
   * @param image The file of its image, deleted when it ends, or {@code null}.
   * @param closed The callback that {@link AbstractApplication} passed to the backend.
   */
  protected AbstractNotification(
      Notification notification, Path image, Consumer<NotificationHandle> closed) {
    this.notification = Objects.requireNonNull(notification, "notification");
    this.image = image;
    this.closedCallback = Objects.requireNonNull(closed, "closed");
  }

  /** The action key of button {@code index}. */
  public static String buttonAction(int index) {
    return BUTTON_ACTION_PREFIX + index;
  }

  @Override
  public final boolean isClosed() {
    return this.closed.get();
  }

  @Override
  public final void close() {
    if (this.closed.compareAndSet(false, true)) {
      this.withdraw();
      this.finish();
    }
  }

  /** Takes the notification off the screen. Called once, from {@link #close()}. */
  protected abstract void withdraw();

  /** Frees what the backend holds for the notification. Called once, when it ends. */
  protected void release() {}

  /** Ends the notification because the desktop let it go: expired, dismissed, or answered. */
  protected final void markClosed() {
    if (this.closed.compareAndSet(false, true)) {
      this.finish();
    }
  }

  /**
   * Runs the handler that {@code action} names, off the UI thread: {@code onActivate} for {@link
   * #DEFAULT_ACTION}, the action of the button for {@link #buttonAction(int)}. Any other key, and
   * any key after the notification ended, does nothing.
   */
  protected final void respond(String action) {
    if (this.closed.get()) {
      return;
    }
    if (DEFAULT_ACTION.equals(action)) {
      HandlerUtil.runOffTheUiThread(this.notification.onActivate());
      return;
    }
    if (action == null || !action.startsWith(BUTTON_ACTION_PREFIX)) {
      return;
    }
    List<NotificationAction> buttons = this.notification.actions();
    try {
      int index = Integer.parseInt(action.substring(BUTTON_ACTION_PREFIX.length()));
      if (index >= 0 && index < buttons.size()) {
        HandlerUtil.runOffTheUiThread(buttons.get(index).action());
      }
    } catch (NumberFormatException _) {
      // Not a key of this library: every button key is numbered.
    }
  }

  private void finish() {
    this.release();
    TemporaryImages.delete(this.image);
    this.closedCallback.accept(this);
  }
}
