package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.menu.CheckMenuItem;
import dev.ivchenko.lwjwae.menu.MenuCommands;
import dev.ivchenko.lwjwae.menu.MenuItem;
import dev.ivchenko.lwjwae.menu.MenuRole;
import dev.ivchenko.lwjwae.menu.SeparatorMenuItem;
import dev.ivchenko.lwjwae.menu.SubmenuItem;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.shortcut.ShortcutKey;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.lang.foreign.MemorySegment;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Builds the menus of Win32 from {@link MenuCommands}, and plays the editing roles.
 *
 * <p>An entry answers with its number, which {@code WM_COMMAND} and {@code TrackPopupMenu} hand
 * back. The accelerator of an entry is only text after a tab, which Windows aligns on the right:
 * the keys themselves reach the window through {@code AcceleratorKeyPressed} of WebView2. An
 * ampersand in a label is doubled, or Windows would underline the next letter.
 */
@UtilityClass
class WindowsMenus {
  /** A menu bar of the entries, which {@link User32#destroyMenu} frees with its submenus. */
  MemorySegment menuBar(MenuCommands commands) {
    MemorySegment bar = User32.createMenu();
    WindowsMenus.fill(bar, commands, commands.items());
    return bar;
  }

  /** A popup menu of the entries, which {@link User32#destroyMenu} frees with its submenus. */
  MemorySegment popupMenu(MenuCommands commands) {
    MemorySegment menu = User32.createPopupMenu();
    WindowsMenus.fill(menu, commands, commands.items());
    return menu;
  }

  /**
   * Plays an editing role on the window with the focus: the keys that the page takes for it,
   * pressed as the keyboard would.
   */
  void performEditing(MenuRole role) {
    ShortcutKey key =
        switch (role) {
          case UNDO -> ShortcutKey.Z;
          case REDO -> ShortcutKey.Y;
          case CUT -> ShortcutKey.X;
          case COPY -> ShortcutKey.C;
          case PASTE -> ShortcutKey.V;
          case SELECT_ALL -> ShortcutKey.A;
          default -> throw new IllegalArgumentException("Not an editing role: " + role);
        };
    User32.sendKeys(User32.VK_CONTROL, WindowsShortcuts.virtualKey(key));
  }

  private void fill(MemorySegment menu, MenuCommands commands, List<MenuItem> items) {
    for (MenuItem item : items) {
      switch (item) {
        case SeparatorMenuItem _ -> User32.appendMenuSeparator(menu);
        case SubmenuItem submenu -> {
          MemorySegment inner = User32.createPopupMenu();
          WindowsMenus.fill(inner, commands, submenu.items());
          User32.appendSubmenu(
              menu, inner, WindowsMenus.text(submenu.label(), null), submenu.enabled());
        }
        default -> {
          int id = commands.id(item);
          User32.appendMenuItem(
              menu,
              id,
              WindowsMenus.text(MenuCommands.label(item), MenuCommands.accelerator(item)),
              MenuCommands.isEnabled(item),
              item instanceof CheckMenuItem && commands.isChecked(id));
        }
      }
    }
  }

  private String text(String label, Shortcut accelerator) {
    String escaped = label.replace("&", "&&");
    return accelerator == null ? escaped : escaped + "\t" + WindowsShortcuts.label(accelerator);
  }
}
