package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.MethodStub;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.macos.binding.Signatures;
import dev.ivchenko.lwjwae.menu.CheckMenuItem;
import dev.ivchenko.lwjwae.menu.MenuCommands;
import dev.ivchenko.lwjwae.menu.MenuItem;
import dev.ivchenko.lwjwae.menu.MenuRole;
import dev.ivchenko.lwjwae.menu.RoleMenuItem;
import dev.ivchenko.lwjwae.menu.SeparatorMenuItem;
import dev.ivchenko.lwjwae.menu.SubmenuItem;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.shortcut.ShortcutKey;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import lombok.experimental.UtilityClass;

/**
 * Builds the menus of AppKit from {@link MenuCommands}, and plays the roles.
 *
 * <p>An entry that does something is an {@code NSMenuItem} with the number of the entry as its tag,
 * and a target of a class defined at runtime, whose one method, an upcall stub, hands the tag to
 * the consumer that the target was made for: the target finds it by its address, as the target of a
 * tray does. A role is an item without a target, which sends its action along the responder chain
 * the way the items of every Mac application do, so Copy copies from whatever has the keyboard and
 * grays out with nothing to copy.
 *
 * <p>The accelerator of an entry is its key equivalent, which AppKit checks against every key press
 * with Command before the page sees it.
 */
@UtilityClass
class MacMenus {
  private final String ACTION = "lwjwaeMenuItemClicked:";
  private final Map<Long, IntConsumer> TARGETS = new ConcurrentHashMap<>();
  private final MemorySegment TARGET_CLASS =
      ObjC.defineClass(
          "LwjwaeMenuTarget",
          ObjC.cls("NSObject"),
          Map.of(
              ACTION,
              new MethodStub(
                  NativeLibraries.upcall(
                      MethodHandles.lookup(),
                      MacMenus.class,
                      "onItemClicked",
                      MethodType.methodType(
                          void.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          MemorySegment.class),
                      Signatures.DELEGATE_1),
                  "v@:@")));

  /** A target that hands the tag of an item clicked to {@code picked}, until {@link #release}. */
  MemorySegment target(IntConsumer picked) {
    MemorySegment target = ObjC.send(ObjC.send(TARGET_CLASS, "alloc"), "init");
    TARGETS.put(target.address(), picked);
    return target;
  }

  /** Forgets and releases {@code target}. */
  void release(MemorySegment target) {
    TARGETS.remove(target.address());
    Foundation.release(target);
  }

  /**
   * Adds {@code items} to {@code menu}, the ones that do something with {@code target}, and puts
   * every item with a check mark in {@code checks} by its number.
   */
  void fill(
      MemorySegment menu,
      MenuCommands commands,
      List<MenuItem> items,
      MemorySegment target,
      Map<Integer, MemorySegment> checks) {
    for (MenuItem item : items) {
      switch (item) {
        case SeparatorMenuItem _ -> AppKit.addMenuSeparator(menu);
        case SubmenuItem submenu -> {
          MemorySegment inner = AppKit.menu();
          MacMenus.fill(inner, commands, submenu.items(), target, checks);
          AppKit.addSubmenuItem(menu, submenu.label(), inner, submenu.enabled());
        }
        case RoleMenuItem role -> {
          Shortcut shortcut = role.role().accelerator();
          AppKit.addItem(
              menu,
              role.label(),
              MemorySegment.NULL,
              MacMenus.selector(role.role()),
              0,
              true,
              MacMenus.keyEquivalent(shortcut),
              MacMenus.modifiers(shortcut),
              false);
        }
        default -> {
          int id = commands.id(item);
          Shortcut shortcut = MenuCommands.accelerator(item);
          boolean check = item instanceof CheckMenuItem;
          MemorySegment added =
              AppKit.addItem(
                  menu,
                  MenuCommands.label(item),
                  target,
                  ACTION,
                  id,
                  MenuCommands.isEnabled(item),
                  MacMenus.keyEquivalent(shortcut),
                  MacMenus.modifiers(shortcut),
                  check && commands.isChecked(id));
          if (check) {
            checks.put(id, added);
          }
        }
      }
    }
  }

  /** Sends the action of {@code role} along the responder chain. */
  void play(MenuRole role) {
    AppKit.sendAction(MacMenus.selector(role));
  }

  /** The action of {@code role}, which the first responder that takes it performs. */
  String selector(MenuRole role) {
    return switch (role) {
      case UNDO -> "undo:";
      case REDO -> "redo:";
      case CUT -> "cut:";
      case COPY -> "copy:";
      case PASTE -> "paste:";
      case SELECT_ALL -> "selectAll:";
      case CLOSE_WINDOW -> "performClose:";
      case MINIMIZE -> "performMiniaturize:";
      case FULLSCREEN -> "toggleFullScreen:";
      case QUIT -> "terminate:";
    };
  }

  /** The key equivalent of {@code shortcut}, empty for none. */
  String keyEquivalent(Shortcut shortcut) {
    if (shortcut == null) {
      return "";
    }
    ShortcutKey key = shortcut.key();
    if (key.label().length() == 1) {
      return key.label().toLowerCase(Locale.ROOT);
    }
    if (key.isFunctionKey()) {
      return String.valueOf((char) (0xF704 + key.ordinal() - ShortcutKey.F1.ordinal()));
    }
    return switch (key) {
      case SPACE -> " ";
      case ENTER -> "\r";
      case TAB -> "\t";
      case ESCAPE -> "\u001b";
      case BACKSPACE -> "\b";
      case DELETE -> "";
      case INSERT -> "";
      case HOME -> "";
      case END -> "";
      case PAGE_UP -> "";
      case PAGE_DOWN -> "";
      case UP -> "";
      case DOWN -> "";
      case LEFT -> "";
      case RIGHT -> "";
      default -> throw new IllegalArgumentException("No key equivalent for " + key);
    };
  }

  /** The modifier mask of the key equivalent of {@code shortcut}. */
  long modifiers(Shortcut shortcut) {
    return shortcut == null
        ? 0
        : shortcut.mask(
            (int) AppKit.MODIFIER_CONTROL,
            (int) AppKit.MODIFIER_OPTION,
            (int) AppKit.MODIFIER_SHIFT,
            (int) AppKit.MODIFIER_COMMAND);
  }

  /**
   * {@code lwjwaeMenuItemClicked:}: hands the tag of the item to the consumer of the target.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private void onItemClicked(MemorySegment self, MemorySegment command, MemorySegment sender) {
    try {
      IntConsumer picked = TARGETS.get(self.address());
      if (picked != null) {
        picked.accept((int) AppKit.menuItemTag(sender));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
