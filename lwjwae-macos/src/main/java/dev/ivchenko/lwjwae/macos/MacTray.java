package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.MethodStub;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.macos.binding.Signatures;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.HandlerUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A tray icon on macOS: an {@code NSStatusItem} in the menu bar.
 *
 * <p>AppKit reports clicks as actions sent to a target object, so every tray has one of its own, of
 * a class defined at runtime whose two methods are upcall stubs: one for the button in the menu
 * bar, one for the entries of the menu. The target finds its tray by its address, as a window
 * delegate finds its window.
 *
 * <p>Without {@link TrayIcon#onActivate()}, the menu belongs to the status item, and AppKit opens
 * it on any click, as it does for every other menu bar extra. With a handler, the button sends its
 * action on a release of either mouse button; the primary one runs the handler, and a secondary or
 * Control click lends the menu to the status item for one {@code performClick:}, which opens the
 * menu and returns when it closes.
 *
 * <p>The menu is rebuilt when its entries change, and each entry carries its position plus one as
 * its tag. Every call that touches AppKit runs on the main thread, and so does every click, so a
 * tag always refers to the entries of the menu that was on screen.
 */
public class MacTray implements Tray {
  private static final Map<Long, MacTray> TARGETS = new ConcurrentHashMap<>();

  private static final String BUTTON_ACTION = "lwjwaeTrayButtonClicked:";
  private static final String MENU_ITEM_ACTION = "lwjwaeTrayMenuItemClicked:";

  private static final MemorySegment TARGET_CLASS =
      ObjC.defineClass(
          "LwjwaeTrayTarget",
          ObjC.cls("NSObject"),
          Map.of(
              BUTTON_ACTION, new MethodStub(MacTray.actionStub("onButtonClicked"), "v@:@"),
              MENU_ITEM_ACTION, new MethodStub(MacTray.actionStub("onMenuItemClicked"), "v@:@")));

  private final UiDispatcher dispatcher;
  private final Consumer<Tray> closedCallback;
  private final Runnable onActivate;

  private volatile MemorySegment statusItem;
  private volatile MemorySegment target;
  private volatile MemorySegment nativeMenu;
  private volatile List<TrayMenuItem> shownItems = List.of();
  private volatile boolean closed;

  /**
   * Adds the item to the menu bar and returns when it's there.
   *
   * @param closed Told once, when the icon goes away, so that the application stops counting it.
   * @throws IllegalArgumentException If AppKit can't read the image.
   */
  MacTray(UiDispatcher dispatcher, TrayIcon icon, Consumer<Tray> closed) {
    this.dispatcher = dispatcher;
    this.closedCallback = closed;
    this.onActivate = icon.onActivate();
    this.dispatcher.run(() -> this.create(icon));
  }

  /** Creates the target, the status item, and the menu. Runs on the main thread, once. */
  private void create(TrayIcon icon) {
    MemorySegment newTarget = ObjC.send(ObjC.send(TARGET_CLASS, "alloc"), "init");
    MemorySegment newItem = AppKit.statusItem();
    try {
      MemorySegment button = AppKit.statusItemButton(newItem);
      AppKit.setButtonImage(button, icon.icon());
      AppKit.setToolTip(button, icon.tooltip());
      if (this.onActivate != null) {
        AppKit.setButtonAction(button, newTarget, BUTTON_ACTION);
      }
    } catch (RuntimeException | Error e) {
      AppKit.removeStatusItem(newItem);
      Foundation.release(newTarget);
      throw e;
    }
    TARGETS.put(newTarget.address(), this);
    this.target = newTarget;
    this.statusItem = newItem;
    this.rebuildMenu(icon.menu());
  }

  @Override
  public void icon(byte[] png) {
    this.checkOpen();
    this.dispatcher.run(() -> AppKit.setButtonImage(this.button(), png));
  }

  @Override
  public void tooltip(String tooltip) {
    this.checkOpen();
    this.dispatcher.run(() -> AppKit.setToolTip(this.button(), tooltip));
  }

  @Override
  public void menu(List<TrayMenuItem> items) {
    this.checkOpen();
    List<TrayMenuItem> copy = List.copyOf(items);
    this.dispatcher.run(
        () -> {
          if (this.statusItem != null) {
            this.rebuildMenu(copy);
          }
        });
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
    this.dispatcher.run(
        () -> {
          MemorySegment item = this.statusItem;
          this.statusItem = null;
          if (item != null) {
            AppKit.setStatusItemMenu(item, MemorySegment.NULL);
            AppKit.removeStatusItem(item);
          }
          this.releaseMenu();
          MemorySegment oldTarget = this.target;
          this.target = null;
          if (oldTarget != null) {
            TARGETS.remove(oldTarget.address());
            Foundation.release(oldTarget);
          }
        });
    this.closedCallback.accept(this);
  }

  /**
   * Clicks the button in the menu bar, as the primary mouse button does. For tests: no test can
   * click the menu bar.
   */
  void simulateClick() {
    this.dispatcher.run(() -> AppKit.performClick(this.button()));
  }

  private MemorySegment button() {
    MemorySegment item = this.statusItem;
    if (item == null) {
      throw new IllegalStateException("The tray icon is closed");
    }
    return AppKit.statusItemButton(item);
  }

  private void checkOpen() {
    if (this.closed) {
      throw new IllegalStateException("The tray icon is closed");
    }
  }

  /** Replaces the menu with one built from {@code items}. Runs on the main thread. */
  private void rebuildMenu(List<TrayMenuItem> items) {
    this.releaseMenu();
    this.shownItems = items;
    if (items.isEmpty()) {
      return;
    }
    MemorySegment menu = AppKit.menu();
    for (int index = 0; index < items.size(); index++) {
      TrayMenuItem item = items.get(index);
      if (item.isSeparator()) {
        AppKit.addMenuSeparator(menu);
      } else {
        AppKit.addMenuItem(
            menu, item.label(), this.target, MENU_ITEM_ACTION, index + 1, item.enabled());
      }
    }
    this.nativeMenu = menu;
    if (this.onActivate == null) {
      AppKit.setStatusItemMenu(this.statusItem, menu);
    }
  }

  private void releaseMenu() {
    MemorySegment menu = this.nativeMenu;
    this.nativeMenu = null;
    if (menu == null) {
      return;
    }
    if (this.onActivate == null && this.statusItem != null) {
      AppKit.setStatusItemMenu(this.statusItem, MemorySegment.NULL);
    }
    Foundation.release(menu);
  }

  /** The button was clicked, and the tray has an {@code onActivate} handler. */
  private void buttonClicked() {
    MemorySegment item = this.statusItem;
    MemorySegment menu = this.nativeMenu;
    if (item == null) {
      return;
    }
    if (!AppKit.isContextClick()) {
      HandlerUtil.runOffTheUiThread(this.onActivate);
      return;
    }
    if (menu != null) {
      AppKit.setStatusItemMenu(item, menu);
      AppKit.performClick(AppKit.statusItemButton(item));
      AppKit.setStatusItemMenu(item, MemorySegment.NULL);
    }
  }

  private void menuItemClicked(long tag) {
    List<TrayMenuItem> items = this.shownItems;
    if (tag < 1 || tag > items.size()) {
      return;
    }
    Runnable action = items.get((int) tag - 1).action();
    if (action != null) {
      HandlerUtil.runOffTheUiThread(action);
    }
  }

  private static MemorySegment actionStub(String method) {
    return NativeLibraries.upcall(
        MethodHandles.lookup(),
        MacTray.class,
        method,
        MethodType.methodType(
            void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
        Signatures.DELEGATE_1);
  }

  // --- LwjwaeTrayTarget methods; every one receives self and _cmd first, as Objective-C passes
  // them ---

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the tray is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the tray and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onButtonClicked(
      MemorySegment self, MemorySegment command, MemorySegment sender) {
    try {
      MacTray tray = TARGETS.get(self.address());
      if (tray != null) {
        tray.buttonClicked();
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the tray is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the tray and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static void onMenuItemClicked(
      MemorySegment self, MemorySegment command, MemorySegment sender) {
    try {
      MacTray tray = TARGETS.get(self.address());
      if (tray != null) {
        tray.menuItemClicked(AppKit.menuItemTag(sender));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
