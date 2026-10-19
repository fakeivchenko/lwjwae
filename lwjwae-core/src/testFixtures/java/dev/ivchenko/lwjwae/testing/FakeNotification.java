package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.notification.NotificationHandle;
import java.util.function.Consumer;

/** A {@link NotificationHandle} without a desktop: it only tells its application when it closes. */
public class FakeNotification implements NotificationHandle {
  private final Consumer<NotificationHandle> closedCallback;

  private volatile boolean closed;

  FakeNotification(Consumer<NotificationHandle> closedCallback) {
    this.closedCallback = closedCallback;
  }

  @Override
  public boolean isClosed() {
    return this.closed;
  }

  @Override
  public void close() {
    if (this.closed) {
      return;
    }
    this.closed = true;
    this.closedCallback.accept(this);
  }
}
