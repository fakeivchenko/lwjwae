package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.testing.contract.DownloadContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class Gtk4DownloadTest extends DownloadContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
