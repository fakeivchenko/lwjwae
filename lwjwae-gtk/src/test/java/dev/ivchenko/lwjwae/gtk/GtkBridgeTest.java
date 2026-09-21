package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.testing.contract.BridgeContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class GtkBridgeTest extends BridgeContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
