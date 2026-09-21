package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.testing.contract.NetworkContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class WindowsNetworkTest extends NetworkContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }
}
