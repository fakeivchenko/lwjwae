package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacWindowTest extends WindowContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }

  @Override
  protected Class<? extends ApplicationBackend> expectedBackendType() {
    return MacApplicationBackend.class;
  }
}
