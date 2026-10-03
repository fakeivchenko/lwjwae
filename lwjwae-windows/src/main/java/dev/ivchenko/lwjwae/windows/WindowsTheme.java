package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.windows.binding.Advapi32;
import lombok.experimental.UtilityClass;

/**
 * The light or dark choice of the user on Windows, which the settings keep as {@code
 * AppsUseLightTheme} in the registry: the "app mode" of Personalization, and the one that
 * applications follow. The "Windows mode" of the taskbar and the Start menu is {@code
 * SystemUsesLightTheme}, and an application doesn't paint either.
 *
 * <p>The application polls the value instead of waiting for {@code WM_SETTINGCHANGE}, which only a
 * top-level window receives: a program that lives in the tray, with no window, would never hear of
 * a change. Reading a value of the registry costs microseconds.
 */
@UtilityClass
class WindowsTheme {
  private final String PERSONALIZE =
      "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
  private final String APPS_USE_LIGHT_THEME = "AppsUseLightTheme";

  /** The theme of the apps now: {@link SystemTheme#LIGHT} without the value, as on Windows 8. */
  SystemTheme read() {
    return Advapi32.readDword(Advapi32.HKEY_CURRENT_USER, PERSONALIZE, APPS_USE_LIGHT_THEME)
                .orElse(1)
            == 0
        ? SystemTheme.DARK
        : SystemTheme.LIGHT;
  }
}
