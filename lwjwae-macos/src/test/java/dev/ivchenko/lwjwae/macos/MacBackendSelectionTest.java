package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.BackendProvider;
import dev.ivchenko.lwjwae.testing.contract.BackendSelectionContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacBackendSelectionTest extends BackendSelectionContractTest {
  @Override
  protected Class<? extends BackendProvider> providerType() {
    return MacBackendProvider.class;
  }

  @Override
  protected String providerName() {
    return "cocoa-wkwebview";
  }

  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }
}
