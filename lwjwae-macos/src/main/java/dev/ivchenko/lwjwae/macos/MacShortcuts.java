package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.exception.ShortcutUnavailableException;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.macos.binding.Carbon;
import dev.ivchenko.lwjwae.macos.binding.Signatures;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.shortcut.ShortcutKey;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;

/**
 * The global shortcuts of the process, as hot keys of Carbon on the application event target, whose
 * one handler hands each press to the shortcut of its ID.
 *
 * <p>A hot key names a key by its place on the keyboard, the virtual key code of an ANSI keyboard,
 * not by what it types: {@code Command+Z} is the key left of {@code X} on every layout, as the menu
 * shortcuts of macOS are.
 */
@UtilityClass
class MacShortcuts {
  private final MemorySegment ON_HOT_KEY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacShortcuts.class,
          "onHotKey",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.EVENT_HANDLER);

  private final AtomicInteger IDS = new AtomicInteger();
  private final Map<Integer, Runnable> BOUND = new ConcurrentHashMap<>();

  /** Whether the handler is installed, once a process. Read and written on the main thread. */
  private boolean handling;

  /** Registers {@code shortcut} on the main thread; {@code pressed} runs there on every press. */
  EventSubscription bind(UiDispatcher dispatcher, Shortcut shortcut, Runnable pressed) {
    int keyCode = MacShortcuts.keyCode(shortcut.key());
    if (keyCode < 0) {
      throw new ShortcutUnavailableException("A Mac keyboard has no " + shortcut.key().label());
    }
    int modifiers =
        shortcut.mask(Carbon.CONTROL_KEY, Carbon.OPTION_KEY, Carbon.SHIFT_KEY, Carbon.COMMAND_KEY);
    int id = IDS.incrementAndGet();
    MemorySegment reference =
        dispatcher.call(
            () -> {
              if (!handling) {
                Carbon.installHotKeyHandler(ON_HOT_KEY);
                handling = true;
              }
              MemorySegment registered = Carbon.registerHotKey(keyCode, modifiers, id);
              if (registered.equals(MemorySegment.NULL)) {
                throw new ShortcutUnavailableException(
                    shortcut + " is a hot key of another application");
              }
              BOUND.put(id, pressed);
              return registered;
            });
    return () -> {
      if (BOUND.remove(id) != null) {
        dispatcher.run(() -> Carbon.unregisterHotKey(reference));
      }
    };
  }

  /** The {@code kVK_} code of {@code key}, or -1 for a key that a Mac keyboard doesn't have. */
  int keyCode(ShortcutKey key) {
    return switch (key) {
      case A -> 0x00;
      case S -> 0x01;
      case D -> 0x02;
      case F -> 0x03;
      case H -> 0x04;
      case G -> 0x05;
      case Z -> 0x06;
      case X -> 0x07;
      case C -> 0x08;
      case V -> 0x09;
      case B -> 0x0B;
      case Q -> 0x0C;
      case W -> 0x0D;
      case E -> 0x0E;
      case R -> 0x0F;
      case Y -> 0x10;
      case T -> 0x11;
      case DIGIT_1 -> 0x12;
      case DIGIT_2 -> 0x13;
      case DIGIT_3 -> 0x14;
      case DIGIT_4 -> 0x15;
      case DIGIT_6 -> 0x16;
      case DIGIT_5 -> 0x17;
      case DIGIT_9 -> 0x19;
      case DIGIT_7 -> 0x1A;
      case DIGIT_8 -> 0x1C;
      case DIGIT_0 -> 0x1D;
      case O -> 0x1F;
      case U -> 0x20;
      case I -> 0x22;
      case P -> 0x23;
      case L -> 0x25;
      case J -> 0x26;
      case K -> 0x28;
      case N -> 0x2D;
      case M -> 0x2E;
      case ENTER -> 0x24;
      case TAB -> 0x30;
      case SPACE -> 0x31;
      case BACKSPACE -> 0x33;
      case ESCAPE -> 0x35;
      case F17 -> 0x40;
      case F18 -> 0x4F;
      case F19 -> 0x50;
      case F20 -> 0x5A;
      case F5 -> 0x60;
      case F6 -> 0x61;
      case F7 -> 0x62;
      case F3 -> 0x63;
      case F8 -> 0x64;
      case F9 -> 0x65;
      case F11 -> 0x67;
      case F13 -> 0x69;
      case F16 -> 0x6A;
      case F14 -> 0x6B;
      case F10 -> 0x6D;
      case F12 -> 0x6F;
      case F15 -> 0x71;
      case INSERT -> 0x72;
      case HOME -> 0x73;
      case PAGE_UP -> 0x74;
      case DELETE -> 0x75;
      case F4 -> 0x76;
      case END -> 0x77;
      case F2 -> 0x78;
      case PAGE_DOWN -> 0x79;
      case F1 -> 0x7A;
      case LEFT -> 0x7B;
      case RIGHT -> 0x7C;
      case DOWN -> 0x7D;
      case UP -> 0x7E;
      default -> -1;
    };
  }

  /**
   * The {@code EventHandlerUPP} of every hot key, on the main thread.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private int onHotKey(MemorySegment callReference, MemorySegment event, MemorySegment userData) {
    try {
      Runnable pressed = BOUND.get(Carbon.hotKeyId(event));
      if (pressed == null) {
        return Carbon.EVENT_NOT_HANDLED;
      }
      pressed.run();
      return Carbon.NO_ERROR;
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      return Carbon.EVENT_NOT_HANDLED;
    }
  }
}
