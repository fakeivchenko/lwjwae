package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.testing.contract.RpcContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class WindowsRpcTest extends RpcContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }
}
