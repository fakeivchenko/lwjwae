package dev.ivchenko.lwjwae.tray;

import java.util.List;
import lombok.Builder;

/**
 * What a tray icon shows and does.
 *
 * <p>The icon is a PNG image, because that is the one format that every platform tray reads as it
 * is. 22 to 32 pixels square is the usual size; the platform scales what it gets. The menu is what
 * most trays show on a click of either button, and on Linux with a StatusNotifier host it is the
 * only interaction the tray has, so an icon with no menu is close to invisible there.
 *
 * <pre>{@code
 * TrayIcon.builder()
 *     .icon(ResourceUtil.read("app/tray.png"))
 *     .tooltip("Docs")
 *     .menu(List.of(
 *         new TrayMenuItem("Show", window::show),
 *         TrayMenuItem.separator(),
 *         new TrayMenuItem("Quit", application::quit)))
 *     .onActivate(window::show)
 *     .build()
 * }</pre>
 *
 * @param icon The PNG bytes of the image. Required.
 * @param tooltip The text that the tray shows on hover, where it shows one. Default: none.
 * @param menu The entries of the menu, in order. Default: none.
 * @param onActivate What happens on the primary click of the icon, where the tray reports one.
 *     Default: nothing.
 */
@Builder(toBuilder = true)
public record TrayIcon(byte[] icon, String tooltip, List<TrayMenuItem> menu, Runnable onActivate) {
  public TrayIcon {
    if (icon == null || icon.length == 0) {
      throw new IllegalArgumentException("A tray icon needs an image");
    }
    if (menu == null) {
      menu = List.of();
    }
    menu = List.copyOf(menu);
  }
}
