package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

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

  @Override
  protected Class<? extends Application> expectedApplicationType() {
    return GtkApplication.class;
  }

  @Override
  protected Class<? extends Window> expectedWindowType() {
    return GtkWindow.class;
  }
}
