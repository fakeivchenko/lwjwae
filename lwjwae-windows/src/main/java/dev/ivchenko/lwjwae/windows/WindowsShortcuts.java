package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.exception.ShortcutUnavailableException;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.shortcut.ShortcutKey;
import dev.ivchenko.lwjwae.shortcut.ShortcutModifier;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;

/**
 * The global shortcuts of the process, bound with {@code RegisterHotKey} to the message window of
 * the dispatcher, whose window procedure hands each {@code WM_HOTKEY} to {@link #pressed}.
 *
 * <p>The message window lives as long as the UI thread, not as long as a window, so a shortcut
 * keeps working while every window is hidden or closed and the application lives in the tray.
 */
@UtilityClass
class WindowsShortcuts {
  /** {@code RegisterHotKey} takes the IDs from 0 to {@code 0xBFFF} from an application. */
  private final int LAST_ID = 0xBFFF;

  private final AtomicInteger IDS = new AtomicInteger();
  private final Map<Integer, Runnable> BOUND = new ConcurrentHashMap<>();

  /** Binds {@code shortcut} on the UI thread; {@code pressed} runs there on every press. */
  EventSubscription bind(WindowsDispatcher dispatcher, Shortcut shortcut, Runnable pressed) {
    int id = IDS.getAndIncrement() % LAST_ID + 1;
    int modifiers =
        shortcut.mask(User32.MOD_CONTROL, User32.MOD_ALT, User32.MOD_SHIFT, User32.MOD_WIN);
    int virtualKey = WindowsShortcuts.virtualKey(shortcut.key());
    dispatcher.run(
        () -> {
          BOUND.put(id, pressed);
          if (!User32.registerHotKey(dispatcher.messageWindow(), id, modifiers, virtualKey)) {
            BOUND.remove(id);
            throw new ShortcutUnavailableException(
                shortcut + " is held by another application or by Windows");
          }
        });
    return () -> {
      if (BOUND.remove(id) != null) {
        dispatcher.run(() -> User32.unregisterHotKey(dispatcher.messageWindow(), id));
      }
    };
  }

  /** A {@code WM_HOTKEY} of the message window, on the UI thread. */
  void pressed(long id) {
    Runnable pressed = BOUND.get((int) id);
    if (pressed != null) {
      pressed.run();
    }
  }

  /**
   * The virtual-key code: the letters and the digits are their ASCII codes, as Windows has them.
   */
  int virtualKey(ShortcutKey key) {
    if (key.label().length() == 1) {
      return key.label().charAt(0);
    }
    if (key.isFunctionKey()) {
      return 0x70 + key.ordinal() - ShortcutKey.F1.ordinal();
    }
    return switch (key) {
      case SPACE -> 0x20;
      case ENTER -> 0x0D;
      case TAB -> 0x09;
      case ESCAPE -> 0x1B;
      case BACKSPACE -> 0x08;
      case DELETE -> 0x2E;
      case INSERT -> 0x2D;
      case HOME -> 0x24;
      case END -> 0x23;
      case PAGE_UP -> 0x21;
      case PAGE_DOWN -> 0x22;
      case LEFT -> 0x25;
      case UP -> 0x26;
      case RIGHT -> 0x27;
      case DOWN -> 0x28;
      default -> throw new IllegalArgumentException("No virtual key for " + key);
    };
  }

  /**
   * The shortcut of {@code virtualKey} with the modifiers that are down while the message being
   * handled was sent, or {@code null} for a key that no shortcut has.
   */
  Shortcut shortcutOf(int virtualKey) {
    for (ShortcutKey key : ShortcutKey.values()) {
      if (WindowsShortcuts.virtualKey(key) != virtualKey) {
        continue;
      }
      Set<ShortcutModifier> modifiers = EnumSet.noneOf(ShortcutModifier.class);
      if (User32.isKeyDown(User32.VK_CONTROL)) {
        modifiers.add(ShortcutModifier.CONTROL);
      }
      if (User32.isKeyDown(User32.VK_MENU)) {
        modifiers.add(ShortcutModifier.ALT);
      }
      if (User32.isKeyDown(User32.VK_SHIFT)) {
        modifiers.add(ShortcutModifier.SHIFT);
      }
      if (User32.isKeyDown(User32.VK_LWIN) || User32.isKeyDown(User32.VK_RWIN)) {
        modifiers.add(ShortcutModifier.META);
      }
      if (modifiers.isEmpty() && !key.isFunctionKey()) {
        return null;
      }
      return new Shortcut(modifiers, key);
    }
    return null;
  }

  /**
   * {@code shortcut} as a menu of Windows shows it after the label, such as {@code Ctrl+Shift+S}.
   */
  String label(Shortcut shortcut) {
    return shortcut.toString().replace("Meta", "Win");
  }
}
