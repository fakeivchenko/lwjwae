package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.testing.contract.PortalThemeContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class Gtk4PortalThemeTest extends PortalThemeContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
