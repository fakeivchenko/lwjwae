package dev.ivchenko.lwjwae.theme;

import dev.ivchenko.lwjwae.event.EventSubscription;
import java.util.function.Consumer;

/**
 * The colors that the user chose for the desktop, light or dark, and their changes. {@link
 * dev.ivchenko.lwjwae.Application#theme()} hands them out.
 *
 * <p>The application reads the theme once as it starts, before any window, and follows the desktop
 * from then on, so {@link #current()} answers at once, without asking the desktop. Pages see the
 * same through {@code lwjwae.theme}.
 *
 * <p>Every method is safe to call from any thread.
 */
public interface Theme {
  /**
   * The colors that the user chose for the desktop, as of the last change. A page sees the same as
   * {@code lwjwae.theme.current()}, and as {@code prefers-color-scheme} where its engine follows
   * the desktop.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: {@code AppsUseLightTheme} of the user in the registry, the "app mode" of the
   *       settings.
   *   <li>macOS: The appearance of the system, light or dark.
   *   <li>Linux, GTK 3: The {@code color-scheme} of the desktop portal, and without a portal the
   *       dark preference or the name of the GTK theme.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @return {@link SystemTheme#LIGHT} where the desktop makes no choice.
   * @throws IllegalStateException If the application is closed.
   */
  SystemTheme current();

  /**
   * Calls {@code listener} when the user switches the desktop between light and dark, and never
   * with the theme that it already has. Pages hear the same through {@code lwjwae.theme.listen}.
   *
   * @param listener Receives the new theme on a virtual thread, in the order of the changes.
   * @return The subscription, to stop listening.
   */
  EventSubscription onChange(Consumer<SystemTheme> listener);
}
