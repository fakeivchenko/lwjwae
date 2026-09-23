package dev.ivchenko.lwjwae.gtk.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the subset of GDK that the backend needs: where the monitors are, so that a window
 * can be centered after it is mapped.
 *
 * <p>Every function here must be called on the GTK thread.
 */
@UtilityClass
public class Gdk {
  private final SymbolLookup GDK = NativeLibraries.load("libgdk-3.so.0", "libgdk-3.so");

  /** {@code struct GdkRectangle { int x, y, width, height; }}. */
  private final MemoryLayout RECTANGLE =
      MemoryLayout.structLayout(
          Signatures.C_INT.withName("x"),
          Signatures.C_INT.withName("y"),
          Signatures.C_INT.withName("width"),
          Signatures.C_INT.withName("height"));

  private final MethodHandle DISPLAY_GET_DEFAULT =
      NativeLibraries.downcall(GDK, "gdk_display_get_default", Signatures.POINTER_VOID);
  private final MethodHandle DISPLAY_GET_MONITOR_AT_WINDOW =
      NativeLibraries.downcall(
          GDK, "gdk_display_get_monitor_at_window", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle MONITOR_GET_WORKAREA =
      NativeLibraries.downcall(GDK, "gdk_monitor_get_workarea", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle WAYLAND_DISPLAY_GET_TYPE =
      NativeLibraries.downcallIfPresent(
          GDK.find("gdk_wayland_display_get_type").isPresent() ? GDK : null,
          "gdk_wayland_display_get_type",
          Signatures.LONG_VOID);

  /**
   * Returns {@code {x, y, width, height}} of the work area of the monitor that holds {@code
   * gdkWindow}: the monitor minus panels and docks.
   */
  @SneakyThrows
  public int[] workareaAt(MemorySegment gdkWindow) {
    MemorySegment display = (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact();
    MemorySegment monitor =
        (MemorySegment) DISPLAY_GET_MONITOR_AT_WINDOW.invokeExact(display, gdkWindow);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rectangle = arena.allocate(RECTANGLE);
      MONITOR_GET_WORKAREA.invokeExact(monitor, rectangle);
      return new int[] {
        rectangle.get(Signatures.C_INT, 0),
        rectangle.get(Signatures.C_INT, 4),
        rectangle.get(Signatures.C_INT, 8),
        rectangle.get(Signatures.C_INT, 12)
      };
    }
  }

  /**
   * Whether GDK talks to a Wayland compositor, where a client can neither place its window nor
   * learn where it is. The Wayland backend of GDK may be compiled out, hence the optional symbol.
   */
  @SneakyThrows
  public boolean isWayland() {
    if (WAYLAND_DISPLAY_GET_TYPE == null) {
      return false;
    }
    MemorySegment display = (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact();
    long waylandType = (long) WAYLAND_DISPLAY_GET_TYPE.invokeExact();
    return Glib.typeCheckInstanceIsA(display, waylandType);
  }
}
