package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.exception.ShortcutUnavailableException;
import dev.ivchenko.lwjwae.glib.binding.X11;
import dev.ivchenko.lwjwae.glib.util.KeysymUtil;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.shortcut.ShortcutModifier;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.ToIntFunction;

/**
 * Global shortcuts on X11: a grab of each key on the root window, which sends the press of the key
 * to this client whichever window has the focus.
 *
 * <p>A grab names the modifiers exactly, so Caps Lock and Num Lock, which are modifiers to X11,
 * would each make a shortcut miss while they are on. Every shortcut is grabbed four times, with and
 * without each of them, and a press matches with them masked out.
 *
 * <p>A key that another client grabbed already fails with {@code BadAccess}. Xlib reports that
 * later, to the error handler, so the backend runs the grabs inside an error trap of GDK and passes
 * in what the trap caught.
 */
public final class X11Shortcuts implements DesktopShortcuts {
  private static final List<Integer> LOCKS =
      List.of(0, X11.LOCK_MASK, X11.MOD2_MASK, X11.LOCK_MASK | X11.MOD2_MASK);
  private static final int IGNORED = X11.LOCK_MASK | X11.MOD2_MASK;

  /** Every open instance, which the one hook of the process into the events of X hands them to. */
  private static final Set<X11Shortcuts> OPEN = new CopyOnWriteArraySet<>();

  private final UiDispatcher dispatcher;
  private final MemorySegment display;
  private final ToIntFunction<Runnable> trapped;
  private final Map<Long, Runnable> bound = new ConcurrentHashMap<>();

  /**
   * Starts without a shortcut, and hears the events of X from the hook of the backend from now on.
   *
   * @param display The {@code Display} of GDK.
   * @param trapped Runs its argument inside an error trap of GDK and returns the X error code that
   *     it caught, or 0.
   */
  public X11Shortcuts(
      UiDispatcher dispatcher, MemorySegment display, ToIntFunction<Runnable> trapped) {
    this.dispatcher = dispatcher;
    this.display = display;
    this.trapped = trapped;
    OPEN.add(this);
  }

  @Override
  public EventSubscription bind(Shortcut shortcut, Runnable pressed) {
    long key =
        this.dispatcher.call(
            () -> {
              int keycode = X11.keycode(this.display, KeysymUtil.name(shortcut.key()));
              if (keycode == 0) {
                throw new ShortcutUnavailableException(
                    "No key of this keyboard types " + shortcut.key().label());
              }
              int modifiers = X11Shortcuts.modifiers(shortcut);
              long root = X11.rootWindow(this.display);
              int error =
                  this.trapped.applyAsInt(
                      () -> {
                        for (int lock : LOCKS) {
                          X11.grabKey(this.display, keycode, modifiers | lock, root);
                        }
                      });
              if (error != 0) {
                this.ungrab(keycode, modifiers);
                throw new ShortcutUnavailableException(
                    shortcut + " is grabbed by another client, X error " + error);
              }
              long grabbed = X11Shortcuts.key(keycode, modifiers);
              this.bound.put(grabbed, pressed);
              return grabbed;
            });
    return () -> {
      if (this.bound.remove(key) != null) {
        this.dispatcher.run(() -> this.ungrab((int) (key >>> 32), (int) key));
      }
    };
  }

  /**
   * Runs the shortcut of {@code event}, an {@code XEvent} that GDK hands the hook of the backend,
   * on the GTK thread.
   *
   * @return Whether the event was the press of a bound shortcut.
   */
  public static boolean handleEvent(MemorySegment event) {
    if (!X11.isKeyPress(event)) {
      return false;
    }
    for (X11Shortcuts shortcuts : OPEN) {
      if (shortcuts.handle(event)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void close() {
    OPEN.remove(this);
    for (Long key : List.copyOf(this.bound.keySet())) {
      if (this.bound.remove(key) != null) {
        this.dispatcher.run(() -> this.ungrab((int) (key >>> 32), (int) (long) key));
      }
    }
  }

  private boolean handle(MemorySegment event) {
    Runnable pressed =
        this.bound.get(X11Shortcuts.key(X11.eventKeycode(event), X11.eventState(event) & ~IGNORED));
    if (pressed == null) {
      return false;
    }
    pressed.run();
    return true;
  }

  private void ungrab(int keycode, int modifiers) {
    long root = X11.rootWindow(this.display);
    int _ =
        this.trapped.applyAsInt(
            () -> {
              for (int lock : LOCKS) {
                X11.ungrabKey(this.display, keycode, modifiers | lock, root);
              }
            });
  }

  private static int modifiers(Shortcut shortcut) {
    int modifiers = 0;
    if (shortcut.has(ShortcutModifier.CONTROL)) {
      modifiers |= X11.CONTROL_MASK;
    }
    if (shortcut.has(ShortcutModifier.ALT)) {
      modifiers |= X11.MOD1_MASK;
    }
    if (shortcut.has(ShortcutModifier.SHIFT)) {
      modifiers |= X11.SHIFT_MASK;
    }
    if (shortcut.has(ShortcutModifier.META)) {
      modifiers |= X11.MOD4_MASK;
    }
    return modifiers;
  }

  private static long key(int keycode, int modifiers) {
    return ((long) keycode << 32) | (modifiers & 0xFFFFFFFFL);
  }
}
