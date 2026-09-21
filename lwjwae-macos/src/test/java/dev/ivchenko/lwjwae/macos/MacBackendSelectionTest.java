package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.ApplicationBackendProvider;
import dev.ivchenko.lwjwae.testing.contract.BackendSelectionContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacBackendSelectionTest extends BackendSelectionContractTest {
  @Override
  protected Class<? extends ApplicationBackendProvider> providerType() {
    return MacApplicationBackendProvider.class;
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
