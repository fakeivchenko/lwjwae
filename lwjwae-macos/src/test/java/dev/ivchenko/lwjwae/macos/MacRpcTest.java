package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.testing.contract.RpcContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacRpcTest extends RpcContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }
}
