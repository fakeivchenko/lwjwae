package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.testing.contract.PortalThemeContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class GtkPortalThemeTest extends PortalThemeContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
