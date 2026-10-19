package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.testing.contract.DownloadContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacDownloadTest extends DownloadContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }
}
