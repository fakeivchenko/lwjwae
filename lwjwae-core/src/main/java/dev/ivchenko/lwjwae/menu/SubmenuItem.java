package dev.ivchenko.lwjwae.menu;

import java.util.List;
import java.util.Objects;

/**
 * An entry that opens a menu of its own. The top level of a menu bar is made of these.
 *
 * @param label The text of the entry.
 * @param enabled Whether the user can open the submenu.
 * @param items The entries of the submenu, in order.
 */
public record SubmenuItem(String label, boolean enabled, List<MenuItem> items) implements MenuItem {
  public SubmenuItem {
    Objects.requireNonNull(label, "label");
    items = items == null ? List.of() : List.copyOf(items);
  }

  /** This entry, enabled or not. */
  public SubmenuItem withEnabled(boolean enabled) {
    return new SubmenuItem(this.label, enabled, this.items);
  }
}
