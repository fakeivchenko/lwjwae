package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.testing.contract.NetworkContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class Gtk4NetworkTest extends NetworkContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
