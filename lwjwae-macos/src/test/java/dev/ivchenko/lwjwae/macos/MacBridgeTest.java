package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.testing.contract.BridgeContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacBridgeTest extends BridgeContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }
}
