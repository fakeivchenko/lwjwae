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

  @Override
  protected Class<? extends Application> expectedApplicationType() {
    return Gtk4Application.class;
  }

  @Override
  protected Class<? extends Window> expectedWindowType() {
    return Gtk4Window.class;
  }
}
