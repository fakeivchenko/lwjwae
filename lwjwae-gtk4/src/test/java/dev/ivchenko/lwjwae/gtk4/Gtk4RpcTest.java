package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.testing.contract.RpcContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class Gtk4RpcTest extends RpcContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
