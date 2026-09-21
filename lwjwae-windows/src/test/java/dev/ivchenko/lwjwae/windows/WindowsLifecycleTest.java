package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.testing.contract.LifecycleContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class WindowsLifecycleTest extends LifecycleContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }
}
