package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class GtkWindowTest extends WindowContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }

  @Override
  protected Class<? extends ApplicationBackend> expectedBackendType() {
    return GtkApplicationBackend.class;
  }
}
