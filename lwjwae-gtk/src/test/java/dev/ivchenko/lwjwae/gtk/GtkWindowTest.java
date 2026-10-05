package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.glib.XdndFileDrag;
import dev.ivchenko.lwjwae.glib.XtestKeyboard;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.nio.file.Path;
import java.util.List;

class GtkWindowTest extends WindowContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }

  /** X11 places windows where asked; Wayland keeps placement with the compositor. */
  @Override
  protected boolean canPlaceWindows() {
    return System.getenv("WAYLAND_DISPLAY") == null || "x11".equals(System.getenv("GDK_BACKEND"));
  }

  /** Wayland gives the clipboard only to the client with the focus that a person gave it. */
  @Override
  protected boolean canUseClipboardUnattended() {
    return this.canPlaceWindows();
  }

  /** Wayland keeps the focus with the compositor. */
  @Override
  protected boolean canTakeFocus() {
    return this.canPlaceWindows();
  }

  /** Wayland tells a client nothing about minimizing. */
  @Override
  protected boolean canTellMinimized() {
    return this.canPlaceWindows();
  }

  /** The portal of Wayland asks the user to confirm a shortcut. */
  @Override
  protected boolean canBindShortcutsUnattended() {
    return this.canPlaceWindows();
  }

  /** XTest presses keys on X11; Wayland lets no client press keys for the others. */
  @Override
  protected boolean pressKeys(Shortcut shortcut) {
    XtestKeyboard.press(shortcut);
    return true;
  }

  /** On X11, as {@link #pressKeys}: the arrow selects the first entry, and Return picks it. */
  @Override
  protected boolean pickFirstEntryOfOpenMenu() {
    if (!this.canPlaceWindows()) {
      return false;
    }
    XtestKeyboard.tap("Down", "Return");
    return true;
  }

  @Override
  protected Class<? extends Application> expectedApplicationType() {
    return GtkApplication.class;
  }

  @Override
  protected Class<? extends Window> expectedWindowType() {
    return GtkWindow.class;
  }

  /** GTK takes the choice from the dark preference of its settings, which a test sets here. */
  @Override
  protected boolean switchSystemTheme(SystemTheme theme) {
    GtkDispatcher.instance()
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

  /** XDND from a small program of another process, pressed through XTest, on X11. */
  @Override
  protected boolean dropFiles(String title, List<Path> files) throws Exception {
    return XdndFileDrag.isX11Session() && XdndFileDrag.dragOnto(title, files);
  }

  /** The transient parent of the child. */
  @Override
  protected Boolean isOwnedBy(Window child, Window parent) {
    return GtkDispatcher.instance()
        .call(
            () ->
                Gtk.windowTransientFor(((GtkWindow) child).window())
                    .equals(((GtkWindow) parent).window()));
  }

  /** A visible modal window transient for the parent. */
  @Override
  protected Boolean isBlockedByModal(Window parent) {
    return GtkDispatcher.instance().call(() -> Gtk.hasModalChild(((GtkWindow) parent).window()));
  }
}
