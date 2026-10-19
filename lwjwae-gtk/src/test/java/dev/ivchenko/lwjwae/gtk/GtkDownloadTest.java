package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.testing.contract.DownloadContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class GtkDownloadTest extends DownloadContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
