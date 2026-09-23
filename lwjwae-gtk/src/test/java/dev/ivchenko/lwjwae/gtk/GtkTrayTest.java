package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.testing.contract.TrayContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class GtkTrayTest extends TrayContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
