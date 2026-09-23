package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.HandlerUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import dev.ivchenko.lwjwae.windows.binding.Shell32;
import dev.ivchenko.lwjwae.windows.binding.Signatures;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Consumer;

/**
 * A tray icon on Windows: an icon in the notification area of the taskbar, through {@code
 * Shell_NotifyIconW}.
 *
 * <p>The shell reports clicks on the icon as messages to a window, so every tray has a hidden tool
 * window of its own, on the UI thread, with a window procedure that turns the clicks into the
 * handlers of {@link TrayIcon}. The primary button runs {@link TrayIcon#onActivate()}, or opens the
 * menu when there is no such handler; the secondary button opens the menu. The menu is built from
 * the current entries each time it opens, with {@code TrackPopupMenu}, which answers with the entry
 * picked, so no menu handle outlives a click.
 *
 * <p>When Explorer restarts, the taskbar is new and has no icons. It broadcasts {@code
 * TaskbarCreated} to every top-level window, and the tray adds its icon again; without that, the
 * icon would be gone for the rest of the process.
 */
public class WindowsTray implements Tray {
  private static final String WINDOW_CLASS = "lwjwae-tray";
  private static final int ICON_ID = 1;
  private static final int CALLBACK_MESSAGE = User32.WM_APP + 1;

  private static final CallbackRegistry<WindowsTray> TRAYS = new CallbackRegistry<>();

  private static final MemorySegment TRAY_PROC =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WindowsTray.class,
          "trayProc",
          MethodType.methodType(long.class, MemorySegment.class, int.class, long.class, long.class),
          Signatures.LONG_POINTER_INT_LONG_LONG);

  private static volatile boolean windowClassRegistered;
  private static volatile int taskbarCreated;

  private final UiDispatcher dispatcher;
  private final Consumer<Tray> closedCallback;
  private final long callbackId;
  private final Runnable onActivate;

  private volatile MemorySegment hwnd;
  private volatile MemorySegment icon;
  private volatile String tooltip;
  private volatile List<TrayMenuItem> menu;
  private volatile boolean closed;

  /**
   * Adds the icon and returns when the shell has it.
   *
   * @param closed Told once, when the icon goes away, so that the application stops counting it.
   * @throws IllegalStateException If the shell refuses the icon.
   * @throws IllegalArgumentException If Windows can't read the image.
   */
  WindowsTray(UiDispatcher dispatcher, TrayIcon icon, Consumer<Tray> closed) {
    this.dispatcher = dispatcher;
    this.closedCallback = closed;
    this.onActivate = icon.onActivate();
    this.tooltip = icon.tooltip();
    this.menu = icon.menu();
    this.callbackId = TRAYS.register(this);
    try {
      this.dispatcher.run(() -> this.create(icon.icon()));
    } catch (RuntimeException | Error e) {
      TRAYS.unregister(this.callbackId);
      throw e;
    }
  }

  /** Creates the window and the icon. Runs on the UI thread, once, from the constructor. */
  private void create(byte[] png) {
    registerWindowClass();
    MemorySegment window = User32.createHiddenToolWindow(WINDOW_CLASS);
    try {
      User32.userData(window, this.callbackId);
      this.icon = User32.iconFromPng(png);
      this.hwnd = window;
      Shell32.add(window, ICON_ID, CALLBACK_MESSAGE, this.icon, this.tooltip);
    } catch (RuntimeException | Error e) {
      this.hwnd = null;
      User32.destroy(window);
      if (this.icon != null) {
        User32.destroyIcon(this.icon);
      }
      throw e;
    }
  }

  @Override
  public void icon(byte[] png) {
    this.checkOpen();
    this.dispatcher.run(
        () -> {
          MemorySegment previous = this.icon;
          MemorySegment next = User32.iconFromPng(png);
          Shell32.modify(this.window(), ICON_ID, Shell32.NIF_ICON, next, null);
          this.icon = next;
          User32.destroyIcon(previous);
        });
  }

  @Override
  public void tooltip(String tooltip) {
    this.checkOpen();
    this.tooltip = tooltip;
    this.dispatcher.run(
        () -> Shell32.modify(this.window(), ICON_ID, Shell32.NIF_TIP, null, this.tooltip));
  }

  @Override
  public void menu(List<TrayMenuItem> items) {
    this.checkOpen();
    this.menu = List.copyOf(items);
  }

  @Override
  public boolean isClosed() {
    return this.closed;
  }

  @Override
  public void close() {
    if (this.closed) {
      return;
    }
    this.closed = true;
    TRAYS.unregister(this.callbackId);
    this.dispatcher.run(
        () -> {
          MemorySegment window = this.hwnd;
          this.hwnd = null;
          if (window != null) {
            Shell32.delete(window, ICON_ID);
            User32.destroy(window);
          }
          MemorySegment current = this.icon;
          this.icon = null;
          if (current != null) {
            User32.destroyIcon(current);
          }
        });
    this.closedCallback.accept(this);
  }

  private MemorySegment window() {
    this.checkOpen();
    MemorySegment window = this.hwnd;
    if (window == null) {
      throw new IllegalStateException("The tray icon is closed");
    }
    return window;
  }

  /**
   * Sends the message that the shell sends for {@code mouseMessage} on the icon, such as {@code
   * WM_LBUTTONUP}, through the window procedure. For tests: no test can click the taskbar.
   */
  void simulateClick(int mouseMessage) {
    User32.send(this.window(), CALLBACK_MESSAGE, ICON_ID, mouseMessage);
  }

  private void checkOpen() {
    if (this.closed) {
      throw new IllegalStateException("The tray icon is closed");
    }
  }

  /** The primary button: {@link TrayIcon#onActivate()}, or the menu when there is none. */
  private void activate() {
    if (this.onActivate != null) {
      HandlerUtil.runOffTheUiThread(this.onActivate);
    } else {
      this.showMenu();
    }
  }

  /**
   * Shows the menu at the pointer and runs the entry picked. Runs on the UI thread, inside the
   * window procedure; {@code TrackPopupMenu} pumps messages until the user picks or dismisses.
   */
  private void showMenu() {
    List<TrayMenuItem> items = this.menu;
    MemorySegment window = this.hwnd;
    if (items.isEmpty() || window == null) {
      return;
    }
    MemorySegment popup = User32.createPopupMenu();
    try {
      for (int index = 0; index < items.size(); index++) {
        TrayMenuItem item = items.get(index);
        if (item.isSeparator()) {
          User32.appendMenuSeparator(popup);
        } else {
          // IDs start at 1: TrackPopupMenu answers 0 for a dismissed menu.
          User32.appendMenuItem(popup, index + 1, item.label(), item.enabled());
        }
      }
      // Without the foreground, the menu doesn't close when the user clicks elsewhere.
      User32.setForeground(window);
      int picked = User32.trackPopupMenu(popup, window);
      // The documented companion of the foreground trick: lets the menu close for good.
      User32.post(window, User32.WM_NULL);
      if (picked > 0) {
        Runnable action = items.get(picked - 1).action();
        if (action != null) {
          HandlerUtil.runOffTheUiThread(action);
        }
      }
    } finally {
      User32.destroyMenu(popup);
    }
  }

  private static synchronized void registerWindowClass() {
    if (windowClassRegistered) {
      return;
    }
    User32.registerClass(WINDOW_CLASS, TRAY_PROC);
    taskbarCreated = User32.registerMessage("TaskbarCreated");
    windowClassRegistered = true;
  }

  // --- the window procedure, bound by name from the upcall stub above; the signature is Win32's
  // ---

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the tray is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the tray and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static long trayProc(
      MemorySegment hwnd, int message, long wordParameter, long longParameter) {
    try {
      WindowsTray tray = TRAYS.lookup(User32.userData(hwnd));
      if (tray != null) {
        if (message == CALLBACK_MESSAGE) {
          int mouse = (int) (longParameter & 0xFFFF);
          if (mouse == User32.WM_LBUTTONUP) {
            tray.activate();
          } else if (mouse == User32.WM_RBUTTONUP || mouse == User32.WM_CONTEXTMENU) {
            tray.showMenu();
          }
          return 0;
        }
        if (message == taskbarCreated && taskbarCreated != 0 && tray.hwnd != null) {
          Shell32.add(tray.hwnd, ICON_ID, CALLBACK_MESSAGE, tray.icon, tray.tooltip);
          return 0;
        }
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return User32.defWindowProc(hwnd, message, wordParameter, longParameter);
  }
}
