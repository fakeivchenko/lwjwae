package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.testing.contract.LifecycleContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class Gtk4LifecycleTest extends LifecycleContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
