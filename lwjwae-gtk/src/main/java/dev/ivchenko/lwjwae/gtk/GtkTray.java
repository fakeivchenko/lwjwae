package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk.binding.AppIndicator;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.HandlerUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A tray icon on Linux, through libappindicator where it exists and {@code GtkStatusIcon} where it
 * doesn't.
 *
 * <p>Linux has two tray protocols. The old one, XEmbed, is what {@code GtkStatusIcon} speaks; it
 * needs X11 and a panel that still offers a legacy tray. The current one, StatusNotifierItem over
 * D-Bus, is what KDE, GNOME with its AppIndicator extension, and most other panels take today, and
 * the only one that works on Wayland. libappindicator (Ayatana or Ubuntu flavor) speaks it, so the
 * tray prefers that library and falls back to the status icon when it isn't installed.
 *
 * <p>Both hosts read the image from a file, and a StatusNotifier host caches it by name, so every
 * image becomes a fresh PNG file in a temporary directory, and every change of the image gets a new
 * name. The directory goes away with the tray.
 *
 * <p>The menu is a {@code GtkMenu} either way. An indicator shows it on any click; a status icon
 * shows it on the secondary button and reports the primary one through {@code activate}, which is
 * where {@link TrayIcon#onActivate()} comes from. An indicator has no primary click of its own, so
 * on that path the handler never runs.
 */
public class GtkTray implements Tray {
  private static final CallbackRegistry<GtkTray> TRAYS = new CallbackRegistry<>();
  private static final CallbackRegistry<Runnable> ACTIONS = new CallbackRegistry<>();
  private static final AtomicLong IDS = new AtomicLong();

  private static final MemorySegment ON_MENU_ITEM =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkTray.class,
          "onMenuItem",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.WIDGET_CALLBACK);
  private static final MemorySegment ON_ACTIVATE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkTray.class,
          "onActivate",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.WIDGET_CALLBACK);
  private static final MemorySegment ON_POPUP_MENU =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkTray.class,
          "onPopupMenu",
          MethodType.methodType(
              void.class, MemorySegment.class, int.class, int.class, MemorySegment.class),
          Signatures.STATUS_ICON_POPUP_MENU_CALLBACK);

  private final UiDispatcher dispatcher;
  private final Consumer<Tray> closedCallback;
  private final long id;
  private final String name;
  private final Path directory;
  private final Runnable onActivate;

  private volatile MemorySegment indicator;
  private volatile MemorySegment statusIcon;
  private volatile MemorySegment menu;
  private volatile List<Long> actionIds = List.of();
  private volatile Path iconFile;
  private volatile boolean closed;

  /**
   * Puts the icon up and returns when the host has it.
   *
   * @param closed Told once, when the icon goes away, so that the application stops counting it.
   * @throws UncheckedIOException If the image can't be written to a temporary file.
   */
  GtkTray(UiDispatcher dispatcher, TrayIcon icon, Consumer<Tray> closed) {
    this.dispatcher = dispatcher;
    this.closedCallback = closed;
    this.id = TRAYS.register(this);
    this.name = "lwjwae-tray-" + IDS.incrementAndGet();
    this.onActivate = icon.onActivate();
    try {
      this.directory = Files.createTempDirectory(this.name);
    } catch (IOException e) {
      TRAYS.unregister(this.id);
      throw new UncheckedIOException(e);
    }
    try {
      Path image = this.writeIcon(icon.icon());
      this.dispatcher.run(() -> this.create(image, icon));
    } catch (RuntimeException | Error e) {
      TRAYS.unregister(this.id);
      this.deleteDirectory();
      throw e;
    }
  }

  /** Builds the native objects. Runs on the GTK thread, once, from the constructor. */
  private void create(Path image, TrayIcon icon) {
    MemorySegment newMenu = this.buildMenu(icon.menu());
    if (AppIndicator.isAvailable()) {
      MemorySegment newIndicator = AppIndicator.create(this.name, this.iconName(image));
      AppIndicator.setIconThemePath(newIndicator, this.directory.toString());
      AppIndicator.setTitle(newIndicator, icon.tooltip());
      AppIndicator.setMenu(newIndicator, newMenu);
      AppIndicator.setActive(newIndicator, true);
      this.indicator = newIndicator;
    } else {
      MemorySegment userData = CallbackRegistry.userData(this.id);
      MemorySegment newIcon = Gtk.statusIconNewFromFile(image.toString());
      Gtk.statusIconSetTooltipText(newIcon, icon.tooltip());
      Glib.signalConnect(newIcon, "activate", ON_ACTIVATE, userData);
      Glib.signalConnect(newIcon, "popup-menu", ON_POPUP_MENU, userData);
      Gtk.statusIconSetVisible(newIcon, true);
      this.statusIcon = newIcon;
    }
    this.menu = newMenu;
  }

  @Override
  public void icon(byte[] png) {
    this.checkOpen();
    Path image = this.writeIcon(png);
    this.dispatcher()
        .run(
            () -> {
              if (this.indicator != null) {
                AppIndicator.setIcon(this.indicator, this.iconName(image), null);
              } else if (this.statusIcon != null) {
                Gtk.statusIconSetFromFile(this.statusIcon, image.toString());
              }
            });
  }

  @Override
  public void tooltip(String tooltip) {
    this.checkOpen();
    this.dispatcher()
        .run(
            () -> {
              if (this.indicator != null) {
                AppIndicator.setTitle(this.indicator, tooltip);
              } else if (this.statusIcon != null) {
                Gtk.statusIconSetTooltipText(this.statusIcon, tooltip);
              }
            });
  }

  @Override
  public void menu(List<TrayMenuItem> items) {
    this.checkOpen();
    this.dispatcher()
        .run(
            () -> {
              MemorySegment newMenu = this.buildMenu(items);
              if (this.indicator != null) {
                AppIndicator.setMenu(this.indicator, newMenu);
              }
              this.menu = newMenu;
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
    TRAYS.unregister(this.id);
    this.dispatcher.run(
        () -> {
          // The menu first: the indicator holds the only reference to it, so releasing the
          // indicator first would free the menu under dropMenu().
          this.dropMenu();
          if (this.indicator != null) {
            AppIndicator.setActive(this.indicator, false);
            Glib.unref(this.indicator);
            this.indicator = null;
          }
          if (this.statusIcon != null) {
            Gtk.statusIconSetVisible(this.statusIcon, false);
            Glib.unref(this.statusIcon);
            this.statusIcon = null;
          }
        });
    this.deleteDirectory();
    this.closedCallback.accept(this);
  }

  /**
   * Builds a {@code GtkMenu} from {@code items} and drops the previous one. Every labeled entry
   * gets its own ID in {@link #ACTIONS}, so one stub serves every item of every tray.
   */
  private MemorySegment buildMenu(List<TrayMenuItem> items) {
    this.dropMenu();
    MemorySegment newMenu = Gtk.menuNew();
    List<Long> ids = new ArrayList<>();
    for (TrayMenuItem item : items) {
      MemorySegment widget;
      if (item.isSeparator()) {
        widget = Gtk.separatorMenuItemNew();
      } else {
        widget = Gtk.menuItemNewWithLabel(item.label());
        Gtk.widgetSetSensitive(widget, item.enabled());
        if (item.action() != null) {
          long actionId = ACTIONS.register(item.action());
          ids.add(actionId);
          Glib.signalConnect(widget, "activate", ON_MENU_ITEM, CallbackRegistry.userData(actionId));
        }
      }
      Gtk.menuShellAppend(newMenu, widget);
    }
    Gtk.widgetShowAll(newMenu);
    this.actionIds = List.copyOf(ids);
    return newMenu;
  }

  /** Destroys the current menu, if any, and forgets its actions. Runs on the GTK thread. */
  private void dropMenu() {
    this.actionIds.forEach(ACTIONS::unregister);
    this.actionIds = List.of();
    MemorySegment current = this.menu;
    if (current != null) {
      this.menu = null;
      Gtk.widgetDestroy(current);
    }
  }

  private UiDispatcher dispatcher() {
    return this.dispatcher;
  }

  private void checkOpen() {
    if (this.closed) {
      throw new IllegalStateException("The tray icon is closed");
    }
  }

  /**
   * Writes {@code png} under a name that no previous image of this tray had, and deletes the
   * previous file. A host that cached the old name can't serve the old image for the new name.
   */
  private Path writeIcon(byte[] png) {
    Path previous = this.iconFile;
    Path file = this.directory.resolve(this.name + "-" + IDS.incrementAndGet() + ".png");
    try {
      Files.write(file, png);
      if (previous != null) {
        Files.deleteIfExists(previous);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    this.iconFile = file;
    return file;
  }

  /**
   * The icon name that libappindicator resolves inside the theme path: the file name minus .png.
   */
  private String iconName(Path image) {
    String fileName = image.getFileName().toString();
    return fileName.substring(0, fileName.length() - ".png".length());
  }

  private void deleteDirectory() {
    try {
      Path current = this.iconFile;
      if (current != null) {
        Files.deleteIfExists(current);
      }
      Files.deleteIfExists(this.directory);
    } catch (IOException e) {
      ThrowableUtil.report(e);
    }
  }

  // --- signal handlers, bound by name from the upcall stubs above; signatures are GTK's ---

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onMenuItem(MemorySegment item, MemorySegment userData) {
    HandlerUtil.runOffTheUiThread(ACTIONS.lookup(userData));
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onActivate(MemorySegment icon, MemorySegment userData) {
    GtkTray tray = TRAYS.lookup(userData);
    if (tray != null) {
      HandlerUtil.runOffTheUiThread(tray.onActivate);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onPopupMenu(
      MemorySegment icon, int button, int activateTime, MemorySegment userData) {
    GtkTray tray = TRAYS.lookup(userData);
    MemorySegment current = tray == null ? null : tray.menu;
    if (current != null) {
      Gtk.menuPopupAtPointer(current);
    }
  }
}
