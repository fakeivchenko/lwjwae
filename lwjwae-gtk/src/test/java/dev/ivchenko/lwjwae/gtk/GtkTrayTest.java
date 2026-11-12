package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.glib.FakeStatusNotifierWatcher;
import dev.ivchenko.lwjwae.testing.contract.TrayContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import org.junit.jupiter.api.BeforeAll;

/**
 * The tray contract on GTK 3, through the way the backend takes on this session.
 *
 * <p>On X11 without a panel, as on CI, that is {@code GtkStatusIcon}, which the contract covers
 * this way. Outside X11, a status icon would never show, so the backend refuses it; a session
 * without a panel there gets a watcher that accepts the items, and the contract covers the
 * StatusNotifierItem.
 */
class GtkTrayTest extends TrayContractTest {
  @BeforeAll
  static void startWatcherOutsideX11() {
    if (PlatformUtil.isUnixDesktop()
        && System.getenv("WAYLAND_DISPLAY") != null
        && !"x11".equals(System.getenv("GDK_BACKEND"))) {
      FakeStatusNotifierWatcher.ensureRunning(GtkDispatcher.instance());
    }
  }

  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }
}
