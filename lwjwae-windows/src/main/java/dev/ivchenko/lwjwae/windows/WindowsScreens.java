package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.ScreenArea;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.windows.binding.MonitorInfo;
import dev.ivchenko.lwjwae.windows.binding.Shcore;
import dev.ivchenko.lwjwae.windows.binding.Signatures;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The monitors of the desktop as {@link Screen}s. Every function here runs on the UI thread.
 *
 * <p>{@code EnumDisplayMonitors} hands each monitor to a callback before it returns, so the
 * callback adds to a list that only the UI thread touches. The areas come in the pixels that this
 * process sees, which are the physical ones for a process that declares itself aware of densities
 * and the ones of 96 DPI for one that doesn't, whose windows Windows scales as a whole; the density
 * is read the same way, so the scale agrees with the areas either way.
 */
@UtilityClass
class WindowsScreens {
  private final MemorySegment MONITOR_PROC =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WindowsScreens.class,
          "onMonitor",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class, long.class),
          Signatures.INT_POINTER_X3_LONG);

  /** The monitors found by the enumeration that runs; the UI thread only. */
  private final List<MemorySegment> FOUND = new ArrayList<>();

  /** Every monitor, the primary one first. */
  List<Screen> all() {
    FOUND.clear();
    User32.enumDisplayMonitors(MONITOR_PROC);
    List<Screen> screens =
        FOUND.stream()
            .map(WindowsScreens::of)
            .sorted(Comparator.comparing(screen -> !screen.primary()))
            .toList();
    FOUND.clear();
    return screens;
  }

  /** The monitor as a screen. */
  Screen of(MemorySegment monitor) {
    MonitorInfo info = User32.monitorInfo(monitor);
    return new Screen(
        info.device(),
        WindowsScreens.area(info.monitor()),
        WindowsScreens.area(info.work()),
        (double) Shcore.dpiForMonitor(monitor) / Shcore.STANDARD_DPI,
        info.primary());
  }

  private ScreenArea area(int[] rect) {
    return new ScreenArea(rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1]);
  }

  /**
   * {@code MONITORENUMPROC}: takes one monitor and goes on.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private int onMonitor(MemorySegment monitor, MemorySegment dc, MemorySegment rect, long data) {
    FOUND.add(monitor);
    return 1;
  }
}
