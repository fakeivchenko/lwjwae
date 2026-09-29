package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.util.KeysymUtil;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.menu.CheckMenuItem;
import dev.ivchenko.lwjwae.menu.MenuCommands;
import dev.ivchenko.lwjwae.menu.MenuItem;
import dev.ivchenko.lwjwae.menu.SeparatorMenuItem;
import dev.ivchenko.lwjwae.menu.SubmenuItem;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Builds the widgets of a menu of GTK 3 from {@link MenuCommands}: a {@code GtkMenuBar} for a
 * window, a {@code GtkMenu} for a context menu.
 *
 * <p>Every entry that does something reports its number to one consumer through {@code activate},
 * with one stub for every item of every menu: the number and the consumer wait in a registry under
 * the ID that the item carries as its data, until {@link #release()}. A check item flips its own
 * mark before it activates, as {@link MenuCommands} flips its state, so the two agree.
 *
 * <p>With an accelerator group, the keys of an entry pick it through the window: GTK matches them
 * against the key pressed in any layout, so {@code Ctrl+S} works on a Cyrillic layout too, and
 * gives them to the menu before the page sees them. Without one, as in a context menu, the keys are
 * only shown.
 */
final class GtkMenuBuilder {
  private static final CallbackRegistry<Runnable> ACTIONS = new CallbackRegistry<>();
  private static final MemorySegment ON_ACTIVATE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkMenuBuilder.class,
          "onActivate",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.WIDGET_CALLBACK);

  private final MenuCommands commands;
  private final IntConsumer picked;
  private final MemorySegment accelGroup;
  private final List<Long> actionIds = new ArrayList<>();

  /**
   * A builder of {@code commands}.
   *
   * @param commands The menu to build.
   * @param picked Gets the number of an entry that the user picks, on the GTK thread.
   * @param accelGroup The group of the window that the keys of the entries go to, or {@code NULL}
   *     to only show them.
   */
  GtkMenuBuilder(MenuCommands commands, IntConsumer picked, MemorySegment accelGroup) {
    this.commands = commands;
    this.picked = picked;
    this.accelGroup = accelGroup;
  }

  /** A {@code GtkMenuBar} of the entries, floating. Runs on the GTK thread. */
  MemorySegment menuBar() {
    MemorySegment bar = Gtk.menuBarNew();
    this.fill(bar, this.commands.items());
    Gtk.widgetShowAll(bar);
    return bar;
  }

  /** A {@code GtkMenu} of the entries, floating. Runs on the GTK thread. */
  MemorySegment menu() {
    MemorySegment menu = Gtk.menuNew();
    this.fill(menu, this.commands.items());
    Gtk.widgetShowAll(menu);
    return menu;
  }

  /**
   * Runs {@code handler} when {@code menu} closes, before the item picked, if any, activates. Runs
   * on the GTK thread.
   */
  void onDeactivate(MemorySegment menu, Runnable handler) {
    long actionId = ACTIONS.register(handler);
    this.actionIds.add(actionId);
    Glib.signalConnect(menu, "deactivate", ON_ACTIVATE, CallbackRegistry.userData(actionId));
  }

  /** Forgets the entries, once the widgets are gone. Runs on the GTK thread. */
  void release() {
    this.actionIds.forEach(ACTIONS::unregister);
    this.actionIds.clear();
  }

  private void fill(MemorySegment shell, List<MenuItem> items) {
    for (MenuItem item : items) {
      Gtk.menuShellAppend(shell, this.widget(item));
    }
  }

  private MemorySegment widget(MenuItem item) {
    if (item instanceof SeparatorMenuItem) {
      return Gtk.separatorMenuItemNew();
    }
    String label = MenuCommands.label(item);
    if (item instanceof SubmenuItem submenu) {
      MemorySegment widget = Gtk.menuItemNewWithLabel(label);
      Gtk.widgetSetSensitive(widget, submenu.enabled());
      MemorySegment menu = Gtk.menuNew();
      this.fill(menu, submenu.items());
      Gtk.menuItemSetSubmenu(widget, menu);
      return widget;
    }
    int id = this.commands.id(item);
    MemorySegment widget;
    if (item instanceof CheckMenuItem) {
      widget = Gtk.checkMenuItemNewWithLabel(label);
      // Before the handler: a change of the mark activates the item.
      Gtk.checkMenuItemSetActive(widget, this.commands.isChecked(id));
    } else {
      widget = Gtk.menuItemNewWithLabel(label);
    }
    Gtk.widgetSetSensitive(widget, MenuCommands.isEnabled(item));
    Shortcut shortcut = MenuCommands.accelerator(item);
    if (shortcut != null) {
      int[] keys = Gtk.acceleratorParse(KeysymUtil.accelerator(shortcut));
      if (keys[0] != 0) {
        Gtk.menuItemShowAccelerator(widget, keys[0], keys[1]);
        if (!MemorySegment.NULL.equals(this.accelGroup)
            && this.commands.acceleratorId(shortcut) == id) {
          Gtk.widgetAddActivateAccelerator(widget, this.accelGroup, keys[0], keys[1]);
        }
      }
    }
    long actionId = ACTIONS.register(() -> this.picked.accept(id));
    this.actionIds.add(actionId);
    Glib.signalConnect(widget, "activate", ON_ACTIVATE, CallbackRegistry.userData(actionId));
    return widget;
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onActivate(MemorySegment item, MemorySegment userData) {
    try {
      Runnable action = ACTIONS.lookup(userData);
      if (action != null) {
        action.run();
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
