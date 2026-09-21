package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class WindowsWindowTest extends WindowContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }

  @Override
  protected Class<? extends ApplicationBackend> expectedBackendType() {
    return WindowsApplicationBackend.class;
  }
}
