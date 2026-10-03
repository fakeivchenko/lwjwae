package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.glib.XtestKeyboard;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk4.binding.Gtk;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class Gtk4WindowTest extends WindowContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }

  /** GTK 4 can't place a window on X11 or on Wayland. */
  @Override
  protected boolean canPlaceWindows() {
    return false;
  }

  /** Wayland tells a client nothing about minimizing. */
  @Override
  protected boolean canTellMinimized() {
    return Gtk4WindowTest.isX11();
  }

  /** Wayland gives the clipboard only to the client with the focus that a person gave it. */
  @Override
  protected boolean canUseClipboardUnattended() {
    return Gtk4WindowTest.isX11();
  }

  /** Wayland keeps the focus with the compositor. */
  @Override
  protected boolean canTakeFocus() {
    return Gtk4WindowTest.isX11();
  }

  /** GTK 4 uses the default size only for the first show; after that, the size is the user's. */
  @Override
  protected boolean canResizeShownWindows() {
    return false;
  }

  private static boolean isX11() {
    return System.getenv("WAYLAND_DISPLAY") == null || "x11".equals(System.getenv("GDK_BACKEND"));
  }

  /** GTK 4 has no way to keep a window above the others. */
  @Override
  protected boolean canKeepOnTop() {
    return false;
  }

  /** GTK 4 has no maximum size. */
  @Override
  protected boolean hasMaximumSize() {
    return false;
  }

  /** The portal of Wayland asks the user to confirm a shortcut. */
  @Override
  protected boolean canBindShortcutsUnattended() {
    return Gtk4WindowTest.isX11();
  }

  /** XTest presses keys on X11; Wayland lets no client press keys for the others. */
  @Override
  protected boolean pressKeys(Shortcut shortcut) {
    XtestKeyboard.press(shortcut);
    return true;
  }

  /** On X11, as {@link #pressKeys}: a popover menu opens with its first entry selected. */
  @Override
  protected boolean pickFirstEntryOfOpenMenu() {
    if (!Gtk4WindowTest.isX11()) {
      return false;
    }
    XtestKeyboard.tap("Return");
    return true;
  }

  @Override
  protected Class<? extends Application> expectedApplicationType() {
    return Gtk4Application.class;
  }

  @Override
  protected Class<? extends Window> expectedWindowType() {
    return Gtk4Window.class;
  }

  /** GTK takes the choice from the dark preference of its settings, which a test sets here. */
  @Override
  protected boolean switchSystemTheme(SystemTheme theme) {
    Gtk4Dispatcher.instance()
        .run(
            () ->
                Glib.setBooleanProperty(
                    Gtk.settingsGetDefault(),
                    "gtk-application-prefer-dark-theme",
                    theme == SystemTheme.DARK));
    return true;
  }

  /** WebKitGTK follows the dark preference of GTK. */
  @Override
  protected boolean engineFollowsTheDesktop() {
    return true;
  }
}
