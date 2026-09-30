package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacWindowTest extends WindowContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }

  @Override
  protected Class<? extends Application> expectedApplicationType() {
    return MacApplication.class;
  }

  @Override
  protected Class<? extends Window> expectedWindowType() {
    return MacWindow.class;
  }

  /** The menu bar is at the top of the screen, not in the window. */
  @Override
  protected boolean hasMenuBarInWindow() {
    return false;
  }
}
