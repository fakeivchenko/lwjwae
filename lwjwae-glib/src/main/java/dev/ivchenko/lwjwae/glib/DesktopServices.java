package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import java.lang.foreign.MemorySegment;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * What a GTK application has of the desktop beyond its windows, the same on GTK 3 and GTK 4: the
 * notifications, through the notification server, and the global shortcuts, through key grabs on
 * X11 and the portal elsewhere. Each connects on its first use rather than at startup.
 *
 * <p>Connecting waits for the GTK thread, so it happens outside the lock of this object: an
 * application that quits on the GTK thread takes that lock to close them, and would otherwise wait
 * for a thread that waits for it. Two threads that connect at once both connect, and the one that
 * comes second closes its own.
 */
public final class DesktopServices {
  private final UiDispatcher dispatcher;
  private final String applicationName;
  private final BooleanSupplier x11;
  private final Supplier<MemorySegment> xlibDisplay;
  private final ToIntFunction<Runnable> trapped;

  private FreedesktopNotifier notifier;
  private DesktopShortcuts shortcuts;
  private boolean closed;

  /**
   * Connects to nothing yet.
   *
   * @param applicationName What the desktop shows as the sender of a notification and next to a
   *     shortcut, or {@code null}.
   * @param x11 Whether the display is one of X11; runs on the GTK thread.
   * @param xlibDisplay The Xlib {@code Display}, once the key presses of X reach {@link
   *     X11Shortcuts#handleEvent}; runs on the GTK thread, on X11 only.
   * @param trapped Runs its argument with the errors of X trapped and returns the one it caused.
   */
  public DesktopServices(
      UiDispatcher dispatcher,
      String applicationName,
      BooleanSupplier x11,
      Supplier<MemorySegment> xlibDisplay,
      ToIntFunction<Runnable> trapped) {
    this.dispatcher = dispatcher;
    this.applicationName = applicationName;
    this.x11 = x11;
    this.xlibDisplay = xlibDisplay;
    this.trapped = trapped;
  }

  /** Shows {@code notification}, see {@link FreedesktopNotifier#show}. */
  public NotificationHandle showNotification(
      Notification notification, Consumer<NotificationHandle> closed) {
    FreedesktopNotifier current;
    synchronized (this) {
      current = this.notifier;
    }
    if (current == null) {
      FreedesktopNotifier connected =
          new FreedesktopNotifier(this.dispatcher, this.applicationName);
      synchronized (this) {
        if (!this.closed && this.notifier == null) {
          this.notifier = connected;
        }
        current = this.notifier;
      }
      if (current != connected) {
        connected.close();
      }
      if (current == null) {
        throw new IllegalStateException("The application is closed");
      }
    }
    return current.show(notification, closed);
  }

  /** Binds {@code shortcut}, see {@link DesktopShortcuts#bind}. */
  public EventSubscription bindShortcut(Shortcut shortcut, Runnable pressed) {
    DesktopShortcuts current;
    synchronized (this) {
      current = this.shortcuts;
    }
    if (current == null) {
      DesktopShortcuts connected = this.connectShortcuts();
      synchronized (this) {
        if (!this.closed && this.shortcuts == null) {
          this.shortcuts = connected;
        }
        current = this.shortcuts;
      }
      if (current != connected) {
        connected.close();
      }
      if (current == null) {
        throw new IllegalStateException("The application is closed");
      }
    }
    return current.bind(shortcut, pressed);
  }

  /** Key grabs on X11; the portal elsewhere, where the compositor keeps the keys from a client. */
  private DesktopShortcuts connectShortcuts() {
    if (this.dispatcher.call(this.x11::getAsBoolean)) {
      return new X11Shortcuts(
          this.dispatcher, this.dispatcher.call(this.xlibDisplay), this.trapped);
    }
    return new PortalShortcuts(
        this.dispatcher, this.applicationName == null ? "Application" : this.applicationName);
  }

  /** Disconnects both, for good. */
  public void close() {
    FreedesktopNotifier currentNotifier;
    DesktopShortcuts currentShortcuts;
    synchronized (this) {
      this.closed = true;
      currentNotifier = this.notifier;
      currentShortcuts = this.shortcuts;
      this.notifier = null;
      this.shortcuts = null;
    }
    if (currentNotifier != null) {
      currentNotifier.close();
    }
    if (currentShortcuts != null) {
      currentShortcuts.close();
    }
  }
}
