package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.testing.contract.BridgeContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class WindowsBridgeTest extends BridgeContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }
}
