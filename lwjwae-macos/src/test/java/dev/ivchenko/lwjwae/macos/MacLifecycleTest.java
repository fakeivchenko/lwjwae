package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.testing.contract.LifecycleContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacLifecycleTest extends LifecycleContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }
}
