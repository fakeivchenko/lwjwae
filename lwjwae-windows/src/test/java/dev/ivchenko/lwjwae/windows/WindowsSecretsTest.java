package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.testing.contract.SecretsContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class WindowsSecretsTest extends SecretsContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }
}
