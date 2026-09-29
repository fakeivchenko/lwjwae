package dev.ivchenko.lwjwae.menu;

import dev.ivchenko.lwjwae.shortcut.Shortcut;
import java.util.List;
import java.util.function.Consumer;

/**
 * One entry of a menu: of the menu bar, of a submenu, or of a context menu.
 *
 * <p>An entry is a value: the menu is built from the entries it has when it's set, and a change
 * sets the menu again. The kinds are the records that this interface permits, and the static
 * methods here create them:
 *
 * <pre>{@code
 * application.menu(
 *     MenuItem.submenu(
 *         "File",
 *         MenuItem.of("Open", "CmdOrCtrl+O", this::open),
 *         MenuItem.separator(),
 *         MenuItem.role(MenuRole.QUIT)),
 *     MenuItem.editMenu(),
 *     MenuItem.submenu(
 *         "View", MenuItem.checkbox("Word Wrap", true, this::wrap)));
 * }</pre>
 *
 * <p>An action runs off the UI thread, like a handler of the tray, so it may block or call back
 * into a window.
 */
public sealed interface MenuItem
    permits ActionMenuItem, CheckMenuItem, SubmenuItem, SeparatorMenuItem, RoleMenuItem {
  /** An entry that runs {@code action}. */
  static ActionMenuItem of(String label, Runnable action) {
    return new ActionMenuItem(label, null, true, action);
  }

  /**
   * An entry that runs {@code action}, also on {@code accelerator} while a window of the
   * application has the keyboard, written as {@link Shortcut#parse} reads it.
   *
   * @throws IllegalArgumentException If the accelerator isn't a shortcut.
   */
  static ActionMenuItem of(String label, String accelerator, Runnable action) {
    return new ActionMenuItem(label, Shortcut.parse(accelerator), true, action);
  }

  /** An entry with a check mark that the user turns on and off; {@code onToggle} gets the state. */
  static CheckMenuItem checkbox(String label, boolean checked, Consumer<Boolean> onToggle) {
    return new CheckMenuItem(label, null, true, checked, onToggle);
  }

  /** An entry that opens {@code items}. */
  static SubmenuItem submenu(String label, MenuItem... items) {
    return new SubmenuItem(label, true, List.of(items));
  }

  /** The same as {@link #submenu(String, MenuItem...)}. */
  static SubmenuItem submenu(String label, List<MenuItem> items) {
    return new SubmenuItem(label, true, items);
  }

  /** A line between two groups of entries. */
  static SeparatorMenuItem separator() {
    return new SeparatorMenuItem();
  }

  /** An entry that does what {@code role} does on the platform, under the label of the platform. */
  static RoleMenuItem role(MenuRole role) {
    return new RoleMenuItem(role, null);
  }

  /** Edit: Undo, Redo, Cut, Copy, Paste, and Select All. */
  static SubmenuItem editMenu() {
    return MenuItem.submenu(
        "Edit",
        MenuItem.role(MenuRole.UNDO),
        MenuItem.role(MenuRole.REDO),
        MenuItem.separator(),
        MenuItem.role(MenuRole.CUT),
        MenuItem.role(MenuRole.COPY),
        MenuItem.role(MenuRole.PASTE),
        MenuItem.role(MenuRole.SELECT_ALL));
  }

  /** Window: Minimize, Full Screen, and Close Window. */
  static SubmenuItem windowMenu() {
    return MenuItem.submenu(
        "Window",
        MenuItem.role(MenuRole.MINIMIZE),
        MenuItem.role(MenuRole.FULLSCREEN),
        MenuItem.separator(),
        MenuItem.role(MenuRole.CLOSE_WINDOW));
  }
}
