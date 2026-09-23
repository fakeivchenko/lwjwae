package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.BackendProvider;
import dev.ivchenko.lwjwae.testing.contract.BackendSelectionContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class WindowsBackendSelectionTest extends BackendSelectionContractTest {
  @Override
  protected Class<? extends BackendProvider> providerType() {
    return WindowsBackendProvider.class;
  }

  @Override
  protected String providerName() {
    return "win32-webview2";
  }

  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }
}
