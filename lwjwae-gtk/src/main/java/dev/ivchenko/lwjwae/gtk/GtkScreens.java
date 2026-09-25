package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.ScreenArea;
import dev.ivchenko.lwjwae.gtk.binding.Gdk;
import java.lang.foreign.MemorySegment;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The monitors of GDK as {@link Screen}s. Every function here runs on the GTK thread.
 *
 * <p>GDK measures monitors in its own pixels, the ones that a window is placed and sized in, so the
 * areas need no conversion; the scale factor says how many device pixels one of them is, as a whole
 * number. Wayland has no primary monitor, and the first one stands for it; it tells no work area,
 * and GDK answers with the whole monitor.
 */
@UtilityClass
class GtkScreens {
  /** Every monitor, the primary one first. */
  List<Screen> all() {
    List<MemorySegment> monitors = Gdk.monitors();
    MemorySegment primary = GtkScreens.primary(monitors);
    return monitors.stream()
        .map(monitor -> GtkScreens.of(monitor, monitor.address() == primary.address()))
        .sorted(Comparator.comparing(screen -> !screen.primary()))
        .toList();
  }

  /** The monitor that holds {@code gdkWindow}, or the primary one for a window not realized. */
  Screen at(MemorySegment gdkWindow) {
    MemorySegment monitor = Gdk.monitorAt(gdkWindow);
    return GtkScreens.of(
        monitor, monitor.address() == GtkScreens.primary(Gdk.monitors()).address());
  }

  /** The primary monitor, or the first where there is none, as on Wayland. */
  private MemorySegment primary(List<MemorySegment> monitors) {
    return monitors.stream().filter(Gdk::isPrimaryMonitor).findFirst().orElse(monitors.getFirst());
  }

  private Screen of(MemorySegment monitor, boolean primary) {
    return new Screen(
        Gdk.monitorName(monitor),
        GtkScreens.area(Gdk.monitorGeometry(monitor)),
        GtkScreens.area(Gdk.monitorWorkarea(monitor)),
        Gdk.monitorScaleFactor(monitor),
        primary);
  }

  private ScreenArea area(int[] rectangle) {
    return new ScreenArea(rectangle[0], rectangle[1], rectangle[2], rectangle[3]);
  }
}
