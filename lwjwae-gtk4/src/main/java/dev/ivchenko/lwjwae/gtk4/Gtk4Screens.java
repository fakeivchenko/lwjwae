package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.ScreenArea;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk4.binding.Gtk;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The monitors of GDK 4 as {@link Screen}s. Every function here runs on the GTK thread.
 *
 * <p>GDK 4 dropped the primary monitor and the work area of GDK 3: the first monitor of the display
 * stands for the primary one, and the work area is the whole monitor. The areas are in the pixels
 * of GDK, the ones a window is sized in.
 */
@UtilityClass
class Gtk4Screens {
  /** Every monitor, the first one as the primary. */
  List<Screen> all() {
    List<Screen> screens = new ArrayList<>();
    for (MemorySegment monitor : Gtk.monitors()) {
      screens.add(Gtk4Screens.of(monitor, screens.isEmpty()));
      Glib.unref(monitor);
    }
    return screens;
  }

  /** The screen that holds most of the window of {@code surface}, or the primary one. */
  Screen at(MemorySegment surface) {
    List<Screen> screens = Gtk4Screens.all();
    MemorySegment monitor = Gtk.monitorAt(surface);
    if (monitor.equals(MemorySegment.NULL)) {
      return screens.getFirst();
    }
    ScreenArea bounds = Gtk4Screens.area(Gtk.monitorGeometry(monitor));
    Glib.unref(monitor);
    return screens.stream()
        .filter(screen -> screen.bounds().equals(bounds))
        .findFirst()
        .orElse(screens.getFirst());
  }

  private Screen of(MemorySegment monitor, boolean primary) {
    ScreenArea bounds = Gtk4Screens.area(Gtk.monitorGeometry(monitor));
    return new Screen(Gtk.monitorName(monitor), bounds, bounds, Gtk.monitorScale(monitor), primary);
  }

  private ScreenArea area(int[] rectangle) {
    return new ScreenArea(rectangle[0], rectangle[1], rectangle[2], rectangle[3]);
  }
}
