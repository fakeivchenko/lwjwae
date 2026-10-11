package dev.ivchenko.lwjwae.menu;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The menus of one window: its menu bar, the menu that a right click on its page opens, and menus
 * that Java opens at the pointer. {@link dev.ivchenko.lwjwae.Window#menus()} hands them out.
 *
 * <p>An entry runs its action off the UI thread, so the action may block or call back into the
 * window. The accelerators of the menu bar work while the window has the keyboard, before the page
 * hears the keys; a menu that opens at the pointer only shows its accelerators.
 *
 * <p>Every method is safe to call from any thread. Once the window is closed, every method throws
 * {@link IllegalStateException}, except {@link #bar()}.
 */
public interface Menus {
  /** The menu bar of the window: its own, or the one of the application. Empty for none. */
  List<MenuItem> bar();

  /**
   * Gives the window a menu bar of its own, instead of the one of the application, see {@link
   * dev.ivchenko.lwjwae.Application#menu(List)}. An empty list takes the bar away from this window.
   * {@link #useApplicationBar()} goes back to the menu of the application.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: The menu bar at the top of the screen shows it while the window is in front.
   *   <li>Linux, GTK 3: As described.
   *   <li>Linux, GTK 4: As described.
   * </ul>
   *
   * @throws IllegalArgumentException If an entry of the top level isn't a submenu.
   * @throws IllegalStateException If the window is closed.
   */
  void bar(List<MenuItem> items);

  /** The same as {@link #bar(List)}. */
  default void bar(MenuItem... items) {
    this.bar(List.of(items));
  }

  /** Makes the window show the menu bar of the application again. */
  void useApplicationBar();

  /**
   * Sets the menu that a right click on the page opens, in place of the one of the engine. The page
   * still decides first: one that handles {@code contextmenu} and calls {@code preventDefault()}
   * keeps the click to itself. An empty list takes the menu away, which leaves the engine as {@link
   * dev.ivchenko.lwjwae.WebView#devToolsEnabled(boolean)} says.
   *
   * <p>A check mark of the menu keeps its state from one opening to the next, until the menu is set
   * again. The accelerators of the entries are only shown: a context menu has no keys.
   *
   * @throws IllegalStateException If the window is closed.
   */
  void context(List<MenuItem> items);

  /** The same as {@link #context(List)}. */
  default void context(MenuItem... items) {
    this.context(List.of(items));
  }

  /**
   * Opens {@code items} as a menu at the pointer, over the window, and completes once it closes and
   * the entry picked, if any, has started. Canceling the future closes the menu, and so does
   * closing the window.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: As described.
   *   <li>macOS: A role goes along the responder chain, the way every Mac menu sends it, and the
   *       page that opened the menu hears of it as of no pick.
   *   <li>Linux, GTK 3: Wayland: the compositor opens the menu only near a click or a key press
   *       that it saw last, which is where the pointer is for a menu opened from one.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @throws IllegalStateException If the window is closed.
   */
  CompletableFuture<Void> popup(List<MenuItem> items);

  /** The same as {@link #popup(List)}. */
  default CompletableFuture<Void> popup(MenuItem... items) {
    return this.popup(List.of(items));
  }
}
