package dev.ivchenko.lwjwae.internal;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.event.WindowEvent;
import dev.ivchenko.lwjwae.event.WindowEventType;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The window events of one window: what turns the notifications of a toolkit into {@link
 * WindowEvent}s, and who hears them.
 *
 * <p>A backend doesn't work out what changed. It reports that something may have, from whichever
 * callbacks its toolkit has, and this class reads the window and compares it with what it read the
 * last time: the events are the same on every platform, a notification that changed nothing yields
 * nothing, and a burst of them, as a drag of the edge of a window makes, yields one reading. The
 * reading waits on the UI thread behind the work that the notification started, so the toolkit is
 * done with the change when it happens.
 *
 * <p>The listeners run on a virtual thread of the window, one event after the other, so a slow one
 * doesn't hold up the UI thread and no listener sees two events out of order.
 */
public final class WindowEvents {
  private final Window window;
  private final Consumer<WindowEvent> page;
  private final Listeners<WindowEvent> listeners = new Listeners<>("lwjwae-window-events");
  private final AtomicBoolean scheduled = new AtomicBoolean();

  private volatile WindowSnapshot last;

  /**
   * Starts from the window as it is now.
   *
   * @param window The window whose events these are.
   * @param page Where the events go for the page, on the UI thread.
   */
  public WindowEvents(Window window, Consumer<WindowEvent> page) {
    this.window = window;
    this.page = page;
  }

  /**
   * Reads the window for the first time: what the first events compare with. Call on the UI thread,
   * before any {@link #changed} can run there.
   */
  public void start() {
    this.last = WindowSnapshot.of(this.window);
  }

  /**
   * Reports that the window may have changed, from any thread. The window is read on {@code
   * uiThread} once the work that is queued there now is done; reports that come before that reading
   * add nothing.
   */
  public void changed(Executor uiThread) {
    if (!this.scheduled.compareAndSet(false, true)) {
      return;
    }
    uiThread.execute(
        () -> {
          this.scheduled.set(false);
          if (this.window.isClosed()) {
            return;
          }
          WindowSnapshot now = WindowSnapshot.of(this.window);
          WindowSnapshot before = this.last;
          this.last = now;
          if (before == null) {
            return;
          }
          List<WindowEventType> changes = now.changesSince(before);
          for (WindowEventType type : changes) {
            WindowEvent event = new WindowEvent(type, this.window, now.size(), now.position());
            this.page.accept(event);
            this.listeners.deliver(event);
          }
        });
  }

  /** Registers {@code listener} for every event from now on. */
  public EventSubscription listen(Consumer<WindowEvent> listener) {
    return this.listeners.listen(listener);
  }

  /** Stops the delivery: the window is gone. */
  public void shutdown() {
    this.listeners.shutdown();
  }
}
