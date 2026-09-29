package dev.ivchenko.lwjwae.menu;

import dev.ivchenko.lwjwae.shortcut.Shortcut;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A menu as a backend builds it: its entries, a number for each one that the user can pick, and the
 * state of its check marks.
 *
 * <p>A native menu reports a pick as a number, a tag, or a callback with one pointer of data, never
 * as a Java object, so every entry that does something gets a number here: an action, a check mark,
 * or a role, from 1 up in the order the entries appear, depth first. 0 is never an entry, which is
 * what {@code TrackPopupMenu} answers for a menu closed without a pick. Two equal entries get two
 * numbers: the entries are values, and the numbers go by identity.
 *
 * <p>The state of a check mark lives here, not in the entry: a pick flips it, and the backend draws
 * what {@link #isChecked} says. Every method is safe to call from any thread.
 */
public final class MenuCommands {
  private final List<MenuItem> items;
  private final Map<MenuItem, Integer> ids = new IdentityHashMap<>();
  private final List<MenuItem> numbered = new ArrayList<>();
  private final Map<Integer, Boolean> checked = Collections.synchronizedMap(new LinkedHashMap<>());
  private final Map<Shortcut, Integer> accelerators = new LinkedHashMap<>();

  /** Numbers the entries of {@code items}, submenus included. */
  public MenuCommands(List<MenuItem> items) {
    this.items = List.copyOf(Objects.requireNonNull(items, "items"));
    this.number(this.items, true);
  }

  /** A menu without entries. */
  public static MenuCommands empty() {
    return new MenuCommands(List.of());
  }

  /** The entries of the top level, in order. */
  public List<MenuItem> items() {
    return this.items;
  }

  /** Whether the menu has no entries. */
  public boolean isEmpty() {
    return this.items.isEmpty();
  }

  /** The number of {@code item}, or 0 for a separator, a submenu, or an entry of another menu. */
  public int id(MenuItem item) {
    Integer id = this.ids.get(item);
    return id == null ? 0 : id;
  }

  /** The entry of {@code id}, or {@code null} for a number that no entry has. */
  public MenuItem item(int id) {
    return id < 1 || id > this.numbered.size() ? null : this.numbered.get(id - 1);
  }

  /** The number of the entry that {@code shortcut} picks, or 0 for none. */
  public int acceleratorId(Shortcut shortcut) {
    Integer id = this.accelerators.get(shortcut);
    return id == null ? 0 : id;
  }

  /** The keys that pick an entry, each with the number of the entry, in the order of the menu. */
  public Map<Shortcut, Integer> accelerators() {
    return Collections.unmodifiableMap(this.accelerators);
  }

  /** Whether the check mark of {@code id} is on. {@code false} for an entry without one. */
  public boolean isChecked(int id) {
    return this.checked.getOrDefault(id, false);
  }

  /**
   * Flips the check mark of {@code id} and returns its state.
   *
   * @throws IllegalArgumentException If the entry has no check mark.
   */
  public boolean toggle(int id) {
    if (!(this.item(id) instanceof CheckMenuItem)) {
      throw new IllegalArgumentException("No check mark: " + id);
    }
    synchronized (this.checked) {
      boolean next = !this.checked.get(id);
      this.checked.put(id, next);
      return next;
    }
  }

  /**
   * The keys that {@code item} shows next to its label: its own, or the ones of its role. {@code
   * null} for none.
   */
  public static Shortcut accelerator(MenuItem item) {
    return switch (item) {
      case ActionMenuItem action -> action.accelerator();
      case CheckMenuItem check -> check.accelerator();
      case RoleMenuItem role -> role.role().accelerator();
      case SubmenuItem _, SeparatorMenuItem _ -> null;
    };
  }

  /** Whether the user can pick {@code item}. A separator can't; a role always can. */
  public static boolean isEnabled(MenuItem item) {
    return switch (item) {
      case ActionMenuItem action -> action.enabled();
      case CheckMenuItem check -> check.enabled();
      case SubmenuItem submenu -> submenu.enabled();
      case RoleMenuItem _ -> true;
      case SeparatorMenuItem _ -> false;
    };
  }

  /** The label of {@code item}, or {@code null} for a separator. */
  public static String label(MenuItem item) {
    return switch (item) {
      case ActionMenuItem action -> action.label();
      case CheckMenuItem check -> check.label();
      case SubmenuItem submenu -> submenu.label();
      case RoleMenuItem role -> role.label();
      case SeparatorMenuItem _ -> null;
    };
  }

  /**
   * Numbers {@code level} and the submenus in it. An entry that the user can't reach, because it or
   * a submenu above it is disabled, gets a number but no keys: they would pick what the menu shows
   * grayed out. The keys of an editing role stay with the page.
   */
  private void number(List<MenuItem> level, boolean reachable) {
    for (MenuItem item : level) {
      switch (item) {
        case SubmenuItem submenu -> this.number(submenu.items(), reachable && submenu.enabled());
        case SeparatorMenuItem _ -> {}
        case ActionMenuItem _, CheckMenuItem _, RoleMenuItem _ -> {
          this.numbered.add(item);
          int id = this.numbered.size();
          this.ids.put(item, id);
          if (item instanceof CheckMenuItem check) {
            this.checked.put(id, check.checked());
          }
          Shortcut shortcut = MenuCommands.accelerator(item);
          boolean editing = item instanceof RoleMenuItem role && role.role().isEditing();
          if (shortcut != null && reachable && MenuCommands.isEnabled(item) && !editing) {
            this.accelerators.putIfAbsent(shortcut, id);
          }
        }
      }
    }
  }
}
