package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.exception.ShortcutUnavailableException;
import dev.ivchenko.lwjwae.shortcut.GlobalShortcuts;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.util.HandlerUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The global shortcuts of one application, and what gives each back to the system.
 *
 * <p>A shortcut is taken in the map under its lock and bound by the backend outside it: a backend
 * binds on the UI thread, and a quit there would otherwise wait on the lock while the registering
 * thread waits on the UI thread. While it is being bound, the shortcut maps to a placeholder, so
 * that a second registration of it fails at once and a quit in the meantime is noticed afterwards.
 */
final class ApplicationShortcuts implements GlobalShortcuts {
  /** What a shortcut maps to while its backend binds it: nothing to give back yet. */
  private static final EventSubscription BINDING = () -> {};

  private final AbstractApplication application;
  private final Map<Shortcut, EventSubscription> bound = new HashMap<>();

  ApplicationShortcuts(AbstractApplication application) {
    this.application = application;
  }

  @Override
  public EventSubscription register(Shortcut shortcut, Runnable handler) {
    Objects.requireNonNull(shortcut, "shortcut");
    Objects.requireNonNull(handler, "handler");
    this.application.checkOpen();
    synchronized (this.bound) {
      if (this.bound.putIfAbsent(shortcut, BINDING) != null) {
        throw new ShortcutUnavailableException(
            shortcut + " is a shortcut of this application already");
      }
    }
    EventSubscription binding;
    try {
      binding =
          this.application.bindGlobalShortcut(
              shortcut, () -> HandlerUtil.runOffTheUiThread(handler));
    } catch (RuntimeException | Error e) {
      synchronized (this.bound) {
        this.bound.remove(shortcut, BINDING);
      }
      throw e;
    }
    boolean kept;
    synchronized (this.bound) {
      kept = this.bound.replace(shortcut, BINDING, binding);
    }
    // The application gave the shortcuts back while this one was being bound: it missed it.
    if (!kept) {
      binding.unlisten();
      throw new IllegalStateException("The application is closed");
    }
    return () -> this.release(shortcut, binding);
  }

  /** Gives every shortcut back to the system. The application calls this as it quits. */
  void releaseAll() {
    List<Map.Entry<Shortcut, EventSubscription>> shortcuts;
    synchronized (this.bound) {
      shortcuts = List.copyOf(this.bound.entrySet());
    }
    for (Map.Entry<Shortcut, EventSubscription> shortcut : shortcuts) {
      try {
        this.release(shortcut.getKey(), shortcut.getValue());
      } catch (Throwable t) {
        ThrowableUtil.report(t);
      }
    }
  }

  private void release(Shortcut shortcut, EventSubscription binding) {
    boolean held;
    synchronized (this.bound) {
      held = this.bound.remove(shortcut, binding);
    }
    if (held) {
      binding.unlisten();
    }
  }
}
