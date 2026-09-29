package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.GioMenus;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.util.KeysymUtil;
import dev.ivchenko.lwjwae.gtk4.binding.Signatures;
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
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Builds a menu of GTK 4 from {@link MenuCommands}: a {@code GMenu} that says what it shows, and a
 * {@code GSimpleActionGroup} with an action {@code itemN} for each entry that does something, which
 * a widget of the window holds under a prefix.
 *
 * <p>A menu model has no separators: a line goes between two sections, so the entries between two
 * separators become one section. A submenu can't be grayed out, so a disabled one disables what it
 * holds. A check mark is the boolean state of its action, set to what {@link MenuCommands} says
 * after each pick.
 *
 * <p>Every action reports its number to one consumer through {@code activate}, with one stub for
 * every action of every menu: the number and the consumer wait in a registry under the ID that the
 * action carries as its data, until {@link #release()}.
 */
final class Gtk4MenuBuilder {
  private static final CallbackRegistry<Runnable> ACTIONS = new CallbackRegistry<>();
  private static final MemorySegment ON_ACTIVATE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4MenuBuilder.class,
          "onActivate",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.ACTION_ACTIVATE_CALLBACK);
  private static final MemorySegment ON_CLOSED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4MenuBuilder.class,
          "onPopoverClosed",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.WIDGET_CALLBACK);

  private final MenuCommands commands;
  private final IntConsumer picked;
  private final String prefix;
  private final List<Long> actionIds = new ArrayList<>();
  private final MemorySegment group;
  private final MemorySegment model;

  /**
   * Builds the model and the actions. Runs on the GTK thread.
   *
   * @param commands The menu to build.
   * @param picked Gets the number of an entry that the user picks, on the GTK thread.
   * @param prefix The prefix that the group of actions goes by in the window.
   */
  Gtk4MenuBuilder(MenuCommands commands, IntConsumer picked, String prefix) {
    this.commands = commands;
    this.picked = picked;
    this.prefix = prefix;
    this.group = GioMenus.actionGroupNew();
    this.model = this.level(commands.items(), true);
  }

  /** The {@code GMenu} of the entries, which this builder owns. */
  MemorySegment model() {
    return this.model;
  }

  /** The group of actions, which this builder owns. */
  MemorySegment group() {
    return this.group;
  }

  /**
   * The keys of the entries, each a pair of an accelerator such as {@code <Control>s} and the name
   * of the action that it runs, with the prefix.
   */
  List<String[]> shortcuts() {
    List<String[]> shortcuts = new ArrayList<>();
    for (Map.Entry<Shortcut, Integer> entry : this.commands.accelerators().entrySet()) {
      shortcuts.add(
          new String[] {
            KeysymUtil.accelerator(entry.getKey()), this.prefix + ".item" + entry.getValue()
          });
    }
    return shortcuts;
  }

  /** Runs {@code handler} when {@code popover} closes. Runs on the GTK thread. */
  void onClosed(MemorySegment popover, Runnable handler) {
    long actionId = ACTIONS.register(handler);
    this.actionIds.add(actionId);
    Glib.signalConnect(popover, "closed", ON_CLOSED, CallbackRegistry.userData(actionId));
  }

  /** Forgets the actions and drops the model and the group. Runs on the GTK thread. */
  void release() {
    this.actionIds.forEach(ACTIONS::unregister);
    this.actionIds.clear();
    Glib.unref(this.model);
    Glib.unref(this.group);
  }

  /** A {@code GMenu} of {@code items}, in sections where separators part them. */
  private MemorySegment level(List<MenuItem> items, boolean reachable) {
    boolean parted = items.stream().anyMatch(SeparatorMenuItem.class::isInstance);
    MemorySegment menu = GioMenus.menuNew();
    MemorySegment section = parted ? GioMenus.menuNew() : menu;
    boolean sectionEmpty = true;
    for (MenuItem item : items) {
      if (item instanceof SeparatorMenuItem) {
        if (!sectionEmpty) {
          GioMenus.appendSection(menu, section);
          Glib.unref(section);
          section = GioMenus.menuNew();
          sectionEmpty = true;
        }
        continue;
      }
      this.append(section, item, reachable);
      sectionEmpty = false;
    }
    if (parted) {
      if (!sectionEmpty) {
        GioMenus.appendSection(menu, section);
      }
      Glib.unref(section);
    }
    return menu;
  }

  private void append(MemorySegment menu, MenuItem item, boolean reachable) {
    String label = MenuCommands.label(item);
    if (item instanceof SubmenuItem submenu) {
      MemorySegment inner = this.level(submenu.items(), reachable && submenu.enabled());
      GioMenus.appendSubmenu(menu, label, inner);
      Glib.unref(inner);
      return;
    }
    int id = this.commands.id(item);
    String name = "item" + id;
    Boolean state = item instanceof CheckMenuItem ? this.commands.isChecked(id) : null;
    MemorySegment action =
        GioMenus.addAction(this.group, name, state, reachable && MenuCommands.isEnabled(item));
    long actionId =
        ACTIONS.register(
            () -> {
              this.picked.accept(id);
              if (state != null) {
                GioMenus.setState(action, this.commands.isChecked(id));
              }
            });
    this.actionIds.add(actionId);
    Glib.signalConnect(action, "activate", ON_ACTIVATE, CallbackRegistry.userData(actionId));
    Shortcut shortcut = MenuCommands.accelerator(item);
    GioMenus.appendItem(
        menu,
        label,
        this.prefix + "." + name,
        shortcut == null ? null : KeysymUtil.accelerator(shortcut));
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onActivate(
      MemorySegment action, MemorySegment parameter, MemorySegment userData) {
    Gtk4MenuBuilder.run(userData);
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onPopoverClosed(MemorySegment popover, MemorySegment userData) {
    Gtk4MenuBuilder.run(userData);
  }

  private static void run(MemorySegment userData) {
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
