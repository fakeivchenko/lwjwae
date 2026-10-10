package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.testing.contract.DownloadContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class WindowsDownloadTest extends DownloadContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }
}
