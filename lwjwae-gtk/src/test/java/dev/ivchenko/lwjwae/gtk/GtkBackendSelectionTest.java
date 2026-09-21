package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.ApplicationBackendProvider;
import dev.ivchenko.lwjwae.testing.contract.BackendSelectionContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class GtkBackendSelectionTest extends BackendSelectionContractTest {
  @Override
  protected Class<? extends ApplicationBackendProvider> providerType() {
    return GtkApplicationBackendProvider.class;
  }

  @Override
  protected String providerName() {
    return "gtk3-webkit2gtk-4.1";
  }

  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
