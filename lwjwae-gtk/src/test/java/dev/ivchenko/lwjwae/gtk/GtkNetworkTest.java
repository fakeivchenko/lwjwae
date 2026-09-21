package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.testing.contract.NetworkContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class GtkNetworkTest extends NetworkContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
