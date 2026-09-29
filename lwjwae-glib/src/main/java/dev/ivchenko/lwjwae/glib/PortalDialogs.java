package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.glib.binding.Dbus;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import java.lang.foreign.MemorySegment;
import lombok.experimental.UtilityClass;

/**
 * Sends the file dialogs of GTK through the desktop portal, so they are the dialogs of the desktop:
 * the one of Dolphin on KDE Plasma, the one of GNOME on GNOME.
 *
 * <p>{@code GtkFileChooserNative} asks the portal only inside a sandbox, unless the environment
 * says otherwise before GTK starts: {@code GTK_USE_PORTAL=1} on GTK 3, and {@code portals} in
 * {@code GDK_DEBUG} on GTK 4. This class says so, but only where the session bus has the portal: a
 * chooser that asks a portal that isn't there shows nothing. A value that the environment of the
 * user has already is left as it is, so {@code GTK_USE_PORTAL=0} or {@code no-portals} keeps the
 * dialog of GTK.
 */
@UtilityClass
public class PortalDialogs {
  private final String PORTAL = "org.freedesktop.portal.Desktop";

  /** For GTK 3; call before {@code gtk_init}. */
  public void preferForGtk3() {
    if (Glib.environment("GTK_USE_PORTAL") == null && PortalDialogs.isAvailable()) {
      Glib.setEnvironment("GTK_USE_PORTAL", "1");
    }
  }

  /** For GTK 4; call before {@code gtk_init}. */
  public void preferForGtk4() {
    String debug = Glib.environment("GDK_DEBUG");
    if ((debug == null || !debug.contains("portals")) && PortalDialogs.isAvailable()) {
      Glib.setEnvironment(
          "GDK_DEBUG", debug == null || debug.isBlank() ? "portals" : debug + ",portals");
    }
  }

  private boolean isAvailable() {
    try {
      MemorySegment bus = Dbus.sessionBus();
      try {
        return Dbus.hasService(bus, PORTAL);
      } finally {
        Glib.unref(bus);
      }
    } catch (RuntimeException _) {
      // No session bus, as in a headless session: no portal either.
      return false;
    }
  }
}
