package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class MacWindowTest extends WindowContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isMacOs();
  }

  @Override
  protected Class<? extends Application> expectedApplicationType() {
    return MacApplication.class;
  }

  @Override
  protected Class<? extends Window> expectedWindowType() {
    return MacWindow.class;
  }

  /** The menu bar is at the top of the screen, not in the window. */
  @Override
  protected boolean hasMenuBarInWindow() {
    return false;
  }

  /**
   * System Events switches the appearance, as the toggle of System Settings does. It asks for the
   * permission to control System Events once, which a CI runner may not have; then the test is
   * skipped.
   */
  @Override
  protected boolean switchSystemTheme(SystemTheme theme) throws Exception {
    Process process =
        new ProcessBuilder(
                "osascript",
                "-e",
                "tell application \"System Events\" to tell appearance preferences to set dark"
                    + " mode to "
                    + (theme == SystemTheme.DARK))
            .redirectErrorStream(true)
            .start();
    process.getInputStream().readAllBytes();
    return process.waitFor() == 0;
  }

  /** The parent window, or the sheet parent of a sheet. */
  @Override
  protected Boolean isOwnedBy(Window child, Window parent) {
    return MacDispatcher.instance()
        .call(
            () ->
                AppKit.parentOf(((MacWindow) child).window())
                    .equals(((MacWindow) parent).window()));
  }

  /** A sheet attached to the parent, which takes no input while it is up. */
  @Override
  protected Boolean isBlockedByModal(Window parent) {
    return MacDispatcher.instance()
        .call(() -> AppKit.hasAttachedSheet(((MacWindow) parent).window()));
  }
}
