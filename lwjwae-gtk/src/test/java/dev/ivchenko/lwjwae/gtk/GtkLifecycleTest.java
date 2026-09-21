package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.testing.contract.LifecycleContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class GtkLifecycleTest extends LifecycleContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
