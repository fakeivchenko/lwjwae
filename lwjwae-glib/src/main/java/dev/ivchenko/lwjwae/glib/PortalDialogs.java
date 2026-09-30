package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.glib.binding.Dbus;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Sends the file dialogs of GTK through the desktop portal, so they are the dialogs of the desktop:
 * the one of Dolphin on KDE Plasma, the one of GNOME on GNOME.
 *
 * <p>{@code GtkFileChooserNative} asks the portal only inside a sandbox, unless the environment
 * says otherwise before GTK starts: {@code GTK_USE_PORTAL=1} on GTK 3, and {@code portals} in
 * {@code GDK_DEBUG} on GTK 4. This class says so, but only where the portal has a file chooser: a
 * portal of a compositor such as {@code xdg-desktop-portal-wlr} has none, and a chooser that asks
 * for it shows nothing. A value that the environment of the user has already is left as it is, so
 * {@code GTK_USE_PORTAL=0} or {@code no-portals} keeps the dialog of GTK.
 *
 * <p>The change is for this process alone. GTK 4 reads {@code GDK_DEBUG} in {@code gtk_init}, so
 * {@link #restoreEnvironment} takes it back right after; GTK 3 reads {@code GTK_USE_PORTAL} at its
 * first dialog, so it stays, and {@link #originalEnvironment} gives the applications that this one
 * opens the environment as it was.
 */
@UtilityClass
public class PortalDialogs {
  private final String PORTAL = "org.freedesktop.portal.Desktop";
  private final String PORTAL_PATH = "/org/freedesktop/portal/desktop";
  private final String FILE_CHOOSER = "org.freedesktop.portal.FileChooser";

  /** The variables that this class changed, and their values before: {@code null} for unset. */
  private final Map<String, String> ORIGINAL = Collections.synchronizedMap(new HashMap<>());

  /** For GTK 3; call before {@code gtk_init}. */
  public void preferForGtk3() {
    if (Glib.environment("GTK_USE_PORTAL") == null && PortalDialogs.hasFileChooser()) {
      PortalDialogs.change("GTK_USE_PORTAL", null, "1");
    }
  }

  /** For GTK 4; call before {@code gtk_init}, and {@link #restoreEnvironment} after it. */
  public void preferForGtk4() {
    String debug = Glib.environment("GDK_DEBUG");
    if ((debug == null || !debug.contains("portals")) && PortalDialogs.hasFileChooser()) {
      PortalDialogs.change(
          "GDK_DEBUG", debug, debug == null || debug.isBlank() ? "portals" : debug + ",portals");
    }
  }

  /** Puts back what this class changed, once GTK has read it. */
  public void restoreEnvironment() {
    synchronized (ORIGINAL) {
      ORIGINAL.forEach(
          (name, value) -> {
            if (value == null) {
              Glib.unsetEnvironment(name);
            } else {
              Glib.setEnvironment(name, value);
            }
          });
      ORIGINAL.clear();
    }
  }

  /**
   * The variables that this class changed and hasn't restored, with their values from before, for a
   * process that the application starts: {@code null} to leave one out.
   */
  public Map<String, String> originalEnvironment() {
    synchronized (ORIGINAL) {
      return new HashMap<>(ORIGINAL);
    }
  }

  private void change(String name, String original, String value) {
    ORIGINAL.put(name, original);
    Glib.setEnvironment(name, value);
  }

  private boolean hasFileChooser() {
    try {
      MemorySegment bus = Dbus.sessionBus();
      try {
        return Dbus.hasProperty(bus, PORTAL, PORTAL_PATH, FILE_CHOOSER, "version");
      } finally {
        Glib.unref(bus);
      }
    } catch (RuntimeException _) {
      // No session bus, as in a headless session: no portal either.
      return false;
    }
  }
}
