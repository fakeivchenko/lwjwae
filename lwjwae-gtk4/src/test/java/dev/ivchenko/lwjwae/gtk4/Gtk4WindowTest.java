package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
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

  @Override
  protected Class<? extends Application> expectedApplicationType() {
    return Gtk4Application.class;
  }

  @Override
  protected Class<? extends Window> expectedWindowType() {
    return Gtk4Window.class;
  }
}
