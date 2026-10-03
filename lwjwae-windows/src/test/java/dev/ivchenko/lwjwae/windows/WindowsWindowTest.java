package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.util.PlatformUtil;

class WindowsWindowTest extends WindowContractTest {
  private static final int DOWN = 0x28;
  private static final int RETURN = 0x0D;

  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }

  @Override
  protected Class<? extends Application> expectedApplicationType() {
    return WindowsApplication.class;
  }

  @Override
  protected Class<? extends Window> expectedWindowType() {
    return WindowsWindow.class;
  }

  @Override
  protected boolean pressKeys(Shortcut shortcut) {
    Keyboard.press(shortcut);
    return true;
  }

  /** A menu of Windows opens with nothing selected: the arrow selects the first entry. */
  @Override
  protected boolean pickFirstEntryOfOpenMenu() {
    Keyboard.tap(DOWN, RETURN);
    return true;
  }

  /** The user switches the "app mode" in the settings, which is a value of the registry. */
  @Override
  protected boolean switchSystemTheme(SystemTheme theme) throws Exception {
    Process process =
        new ProcessBuilder(
                "reg",
                "add",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v",
                "AppsUseLightTheme",
                "/t",
                "REG_DWORD",
                "/d",
                theme == SystemTheme.DARK ? "0" : "1",
                "/f")
            .redirectErrorStream(true)
            .start();
    process.getInputStream().readAllBytes();
    return process.waitFor() == 0;
  }
}
