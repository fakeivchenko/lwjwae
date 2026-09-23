package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.testing.contract.BridgeContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class Gtk4BridgeTest extends BridgeContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
