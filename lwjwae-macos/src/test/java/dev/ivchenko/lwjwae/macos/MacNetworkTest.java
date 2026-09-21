package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.testing.contract.NetworkContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacNetworkTest extends NetworkContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }
}
