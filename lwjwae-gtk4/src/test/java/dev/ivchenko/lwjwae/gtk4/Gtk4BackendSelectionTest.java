package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.BackendProvider;
import dev.ivchenko.lwjwae.testing.contract.BackendSelectionContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class Gtk4BackendSelectionTest extends BackendSelectionContractTest {
  @Override
  protected Class<? extends BackendProvider> providerType() {
    return Gtk4BackendProvider.class;
  }

  @Override
  protected String providerName() {
    return "gtk4-webkitgtk-6.0";
  }

  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
