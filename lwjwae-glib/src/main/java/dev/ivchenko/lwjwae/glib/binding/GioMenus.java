package dev.ivchenko.lwjwae.glib.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the menu models and the actions of GIO, which GTK 4 builds its menus from: a {@code
 * GMenu} says what a menu shows, and each entry names a {@code GSimpleAction} in a group that a
 * widget holds.
 */
@UtilityClass
public class GioMenus {
  private final SymbolLookup GIO = NativeLibraries.load("libgio-2.0.so.0", "libgio-2.0.so");

  private final MethodHandle MENU_NEW =
      NativeLibraries.downcall(GIO, "g_menu_new", Signatures.POINTER_VOID);
  private final MethodHandle MENU_ITEM_NEW =
      NativeLibraries.downcall(GIO, "g_menu_item_new", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle MENU_ITEM_NEW_SUBMENU =
      NativeLibraries.downcall(GIO, "g_menu_item_new_submenu", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle MENU_ITEM_NEW_SECTION =
      NativeLibraries.downcall(GIO, "g_menu_item_new_section", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle MENU_ITEM_SET_ATTRIBUTE_VALUE =
      NativeLibraries.downcall(
          GIO, "g_menu_item_set_attribute_value", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle MENU_APPEND_ITEM =
      NativeLibraries.downcall(GIO, "g_menu_append_item", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle SIMPLE_ACTION_GROUP_NEW =
      NativeLibraries.downcall(GIO, "g_simple_action_group_new", Signatures.POINTER_VOID);
  private final MethodHandle ACTION_MAP_ADD_ACTION =
      NativeLibraries.downcall(GIO, "g_action_map_add_action", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle SIMPLE_ACTION_NEW =
      NativeLibraries.downcall(GIO, "g_simple_action_new", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle SIMPLE_ACTION_NEW_STATEFUL =
      NativeLibraries.downcall(
          GIO, "g_simple_action_new_stateful", Signatures.POINTER_POINTER_POINTER_POINTER);
  private final MethodHandle SIMPLE_ACTION_SET_ENABLED =
      NativeLibraries.downcall(GIO, "g_simple_action_set_enabled", Signatures.VOID_POINTER_INT);
  private final MethodHandle SIMPLE_ACTION_SET_STATE =
      NativeLibraries.downcall(GIO, "g_simple_action_set_state", Signatures.VOID_POINTER_POINTER);

  /** Calls {@code g_menu_new}. The caller owns the menu. */
  @SneakyThrows
  public MemorySegment menuNew() {
    return (MemorySegment) MENU_NEW.invokeExact();
  }

  /**
   * Appends an entry of {@code label} that activates {@code action}, such as {@code menu.item3},
   * with the accelerator that GTK shows next to it, or {@code null} for none.
   */
  @SneakyThrows
  public void appendItem(MemorySegment menu, String label, String action, String accelerator) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment item =
          (MemorySegment)
              MENU_ITEM_NEW.invokeExact(arena.allocateFrom(label), arena.allocateFrom(action));
      if (accelerator != null) {
        MENU_ITEM_SET_ATTRIBUTE_VALUE.invokeExact(
            item, arena.allocateFrom("accel"), Dbus.string(accelerator));
      }
      GioMenus.append(menu, item);
    }
  }

  /**
   * Appends an entry of {@code label} that opens {@code submenu}, which it takes a reference to.
   */
  @SneakyThrows
  public void appendSubmenu(MemorySegment menu, String label, MemorySegment submenu) {
    try (Arena arena = Arena.ofConfined()) {
      GioMenus.append(
          menu,
          (MemorySegment) MENU_ITEM_NEW_SUBMENU.invokeExact(arena.allocateFrom(label), submenu));
    }
  }

  /**
   * Appends {@code section}, which it takes a reference to: the entries of a section are set apart
   * from their neighbors by a line.
   */
  @SneakyThrows
  public void appendSection(MemorySegment menu, MemorySegment section) {
    GioMenus.append(
        menu, (MemorySegment) MENU_ITEM_NEW_SECTION.invokeExact(MemorySegment.NULL, section));
  }

  /** Calls {@code g_simple_action_group_new}. The caller owns the group. */
  @SneakyThrows
  public MemorySegment actionGroupNew() {
    return (MemorySegment) SIMPLE_ACTION_GROUP_NEW.invokeExact();
  }

  /**
   * Adds an action of {@code name} to {@code group} and returns it; the group holds it. A {@code
   * null} state makes an action without one; a check mark is a boolean state.
   */
  @SneakyThrows
  public MemorySegment addAction(MemorySegment group, String name, Boolean state, boolean enabled) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment action =
          state == null
              ? (MemorySegment)
                  SIMPLE_ACTION_NEW.invokeExact(arena.allocateFrom(name), MemorySegment.NULL)
              : (MemorySegment)
                  SIMPLE_ACTION_NEW_STATEFUL.invokeExact(
                      arena.allocateFrom(name), MemorySegment.NULL, Dbus.bool(state));
      SIMPLE_ACTION_SET_ENABLED.invokeExact(action, enabled ? 1 : 0);
      ACTION_MAP_ADD_ACTION.invokeExact(group, action);
      Glib.unref(action);
      return action;
    }
  }

  /** Sets the boolean state of {@code action}, its check mark. */
  @SneakyThrows
  public void setState(MemorySegment action, boolean state) {
    SIMPLE_ACTION_SET_STATE.invokeExact(action, Dbus.bool(state));
  }

  @SneakyThrows
  private void append(MemorySegment menu, MemorySegment item) {
    MENU_APPEND_ITEM.invokeExact(menu, item);
    Glib.unref(item);
  }
}
