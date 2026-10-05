package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.testing.contract.SecretsContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacSecretsTest extends SecretsContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }
}
