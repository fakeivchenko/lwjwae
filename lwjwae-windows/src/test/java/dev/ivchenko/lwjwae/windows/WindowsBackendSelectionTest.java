package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.ApplicationBackendProvider;
import dev.ivchenko.lwjwae.testing.contract.BackendSelectionContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class WindowsBackendSelectionTest extends BackendSelectionContractTest {
  @Override
  protected Class<? extends ApplicationBackendProvider> providerType() {
    return WindowsApplicationBackendProvider.class;
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
