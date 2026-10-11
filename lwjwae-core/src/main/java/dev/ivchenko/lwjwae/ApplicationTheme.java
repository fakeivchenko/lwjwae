package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.internal.Listeners;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.theme.Theme;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The theme of the desktop as one application last heard of it, and the listeners of its changes.
 *
 * <p>The backend reports the theme, see {@link AbstractApplication#themeChanged}, rather than being
 * asked for it, because the desktops tell of a change in their own ways and times: a signal of the
 * portal on Linux, a distributed notification on macOS, and a poll of the registry on Windows. A
 * report of the theme that the application has already changes nothing, so a backend may report
 * more often than the theme changes.
 */
final class ApplicationTheme implements Theme {
  private final AbstractApplication application;

  /** The listeners of a change, which hear one at a time and in order, off the UI thread. */
  private final Listeners<SystemTheme> listeners = new Listeners<>("lwjwae-theme");

  private volatile SystemTheme current = SystemTheme.LIGHT;

  ApplicationTheme(AbstractApplication application) {
    this.application = application;
  }

  @Override
  public SystemTheme current() {
    this.application.checkOpen();
    return this.current;
  }

  @Override
  public EventSubscription onChange(Consumer<SystemTheme> listener) {
    Objects.requireNonNull(listener, "listener");
    return this.listeners.listen(listener);
  }

  /** The desktop is, or may be, in {@code newTheme}: the listeners hear of a change. */
  void changed(SystemTheme newTheme) {
    Objects.requireNonNull(newTheme, "newTheme");
    if (this.current == newTheme) {
      return;
    }
    this.current = newTheme;
    this.listeners.deliver(newTheme);
  }

  /** Stops the delivery: the application is gone. */
  void shutdown() {
    this.listeners.shutdown();
  }
}
