package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.testing.contract.RpcContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class GtkRpcTest extends RpcContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
