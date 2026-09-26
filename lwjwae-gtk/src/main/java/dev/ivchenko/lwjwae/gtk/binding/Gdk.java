package dev.ivchenko.lwjwae.gtk.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the subset of GDK that the backend needs: where the monitors are, so that a window
 * can be centered after it is mapped, and the state of a window that the window manager reports.
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

  private final MethodHandle SCREEN_GET_RGBA_VISUAL =
      NativeLibraries.downcall(GDK, "gdk_screen_get_rgba_visual", Signatures.POINTER_POINTER);
  private final MethodHandle DISPLAY_GET_DEFAULT =
      NativeLibraries.downcall(GDK, "gdk_display_get_default", Signatures.POINTER_VOID);
  private final MethodHandle DISPLAY_GET_MONITOR_AT_WINDOW =
      NativeLibraries.downcall(
          GDK, "gdk_display_get_monitor_at_window", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle MONITOR_GET_WORKAREA =
      NativeLibraries.downcall(GDK, "gdk_monitor_get_workarea", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle MONITOR_GET_GEOMETRY =
      NativeLibraries.downcall(GDK, "gdk_monitor_get_geometry", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle MONITOR_GET_SCALE_FACTOR =
      NativeLibraries.downcall(GDK, "gdk_monitor_get_scale_factor", Signatures.INT_POINTER);
  private final MethodHandle MONITOR_IS_PRIMARY =
      NativeLibraries.downcall(GDK, "gdk_monitor_is_primary", Signatures.INT_POINTER);
  private final MethodHandle MONITOR_GET_MODEL =
      NativeLibraries.downcall(GDK, "gdk_monitor_get_model", Signatures.POINTER_POINTER);
  private final MethodHandle MONITOR_GET_MANUFACTURER =
      NativeLibraries.downcall(GDK, "gdk_monitor_get_manufacturer", Signatures.POINTER_POINTER);
  private final MethodHandle DISPLAY_GET_N_MONITORS =
      NativeLibraries.downcall(GDK, "gdk_display_get_n_monitors", Signatures.INT_POINTER);
  private final MethodHandle DISPLAY_GET_MONITOR =
      NativeLibraries.downcall(GDK, "gdk_display_get_monitor", Signatures.POINTER_POINTER_INT);
  private final MethodHandle DISPLAY_GET_PRIMARY_MONITOR =
      NativeLibraries.downcall(GDK, "gdk_display_get_primary_monitor", Signatures.POINTER_POINTER);
  private final MethodHandle DISPLAY_GET_DEFAULT_SEAT =
      NativeLibraries.downcall(GDK, "gdk_display_get_default_seat", Signatures.POINTER_POINTER);
  private final MethodHandle SEAT_GET_POINTER =
      NativeLibraries.downcall(GDK, "gdk_seat_get_pointer", Signatures.POINTER_POINTER);
  private final MethodHandle DEVICE_GET_POSITION =
      NativeLibraries.downcall(
          GDK, "gdk_device_get_position", Signatures.VOID_POINTER_POINTER_POINTER_POINTER);
  private final MethodHandle DEVICE_GET_STATE =
      NativeLibraries.downcall(
          GDK, "gdk_device_get_state", Signatures.VOID_POINTER_POINTER_POINTER_POINTER);
  private final MethodHandle WINDOW_GET_STATE =
      NativeLibraries.downcall(GDK, "gdk_window_get_state", Signatures.INT_POINTER);
  private final MethodHandle WAYLAND_DISPLAY_GET_TYPE =
      NativeLibraries.downcallIfPresent(
          GDK.find("gdk_wayland_display_get_type").isPresent() ? GDK : null,
          "gdk_wayland_display_get_type",
          Signatures.LONG_VOID);

  /** {@code GDK_CURRENT_TIME}: the time of the event being handled, for a request. */
  public final int CURRENT_TIME = 0;

  /** {@code GDK_WINDOW_EDGE_NORTH_WEST}; the other edges follow clockwise from the west ones. */
  public final int EDGE_NORTH_WEST = 0;

  /** {@code GDK_WINDOW_EDGE_NORTH}. */
  public final int EDGE_NORTH = 1;

  /** {@code GDK_WINDOW_EDGE_NORTH_EAST}. */
  public final int EDGE_NORTH_EAST = 2;

  /** {@code GDK_WINDOW_EDGE_WEST}. */
  public final int EDGE_WEST = 3;

  /** {@code GDK_WINDOW_EDGE_EAST}. */
  public final int EDGE_EAST = 4;

  /** {@code GDK_WINDOW_EDGE_SOUTH_WEST}. */
  public final int EDGE_SOUTH_WEST = 5;

  /** {@code GDK_WINDOW_EDGE_SOUTH}. */
  public final int EDGE_SOUTH = 6;

  /** {@code GDK_WINDOW_EDGE_SOUTH_EAST}. */
  public final int EDGE_SOUTH_EAST = 7;

  /** {@code GDK_BUTTON1_MASK}: the first button is down. */
  private final int BUTTON1_MASK = 1 << 8;

  /** {@code GDK_WINDOW_STATE_ICONIFIED}. */
  public final int STATE_ICONIFIED = 1 << 1;

  /** {@code GDK_WINDOW_STATE_FULLSCREEN}. */
  public final int STATE_FULLSCREEN = 1 << 4;

  /**
   * Calls {@code gdk_screen_get_rgba_visual}: the visual with an alpha channel, or {@code NULL}
   * when the screen has none.
   */
  @SneakyThrows
  public MemorySegment screenRgbaVisual(MemorySegment screen) {
    return (MemorySegment) SCREEN_GET_RGBA_VISUAL.invokeExact(screen);
  }

  /**
   * Calls {@code gdk_window_get_state}: the {@code GdkWindowState} flags that the window manager
   * last reported, or none for a window that isn't realized yet.
   */
  @SneakyThrows
  public int windowState(MemorySegment gdkWindow) {
    if (gdkWindow.equals(MemorySegment.NULL)) {
      return 0;
    }
    return (int) WINDOW_GET_STATE.invokeExact(gdkWindow);
  }

  /**
   * Returns {@code {x, y}} of the pointer on the screen, where a drag that the window manager takes
   * over starts, or {@code null} when the first button is up, as it may be after a quick click. A
   * drag that starts then would stay with a window manager of X11 until the next click. Wayland
   * doesn't tell a client where the pointer is, and the answer there is what GDK last saw.
   */
  @SneakyThrows
  public int[] pressedPointerPosition(MemorySegment gdkWindow) {
    MemorySegment display = (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact();
    MemorySegment seat = (MemorySegment) DISPLAY_GET_DEFAULT_SEAT.invokeExact(display);
    MemorySegment pointer = (MemorySegment) SEAT_GET_POINTER.invokeExact(seat);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment mask = arena.allocate(Signatures.C_INT);
      DEVICE_GET_STATE.invokeExact(pointer, gdkWindow, MemorySegment.NULL, mask);
      if ((mask.get(Signatures.C_INT, 0) & BUTTON1_MASK) == 0) {
        return null;
      }
      MemorySegment x = arena.allocate(Signatures.C_INT);
      MemorySegment y = arena.allocate(Signatures.C_INT);
      DEVICE_GET_POSITION.invokeExact(pointer, MemorySegment.NULL, x, y);
      return new int[] {x.get(Signatures.C_INT, 0), y.get(Signatures.C_INT, 0)};
    }
  }

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

  /** The monitors of the default display, which GDK owns. */
  @SneakyThrows
  public List<MemorySegment> monitors() {
    MemorySegment display = (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact();
    int count = (int) DISPLAY_GET_N_MONITORS.invokeExact(display);
    List<MemorySegment> monitors = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      monitors.add((MemorySegment) DISPLAY_GET_MONITOR.invokeExact(display, index));
    }
    return monitors;
  }

  /**
   * The monitor that holds most of {@code gdkWindow}, or the primary one, or the first, for a
   * window that isn't realized yet.
   */
  @SneakyThrows
  public MemorySegment monitorAt(MemorySegment gdkWindow) {
    MemorySegment display = (MemorySegment) DISPLAY_GET_DEFAULT.invokeExact();
    MemorySegment monitor =
        gdkWindow.equals(MemorySegment.NULL)
            ? MemorySegment.NULL
            : (MemorySegment) DISPLAY_GET_MONITOR_AT_WINDOW.invokeExact(display, gdkWindow);
    if (monitor.equals(MemorySegment.NULL)) {
      monitor = (MemorySegment) DISPLAY_GET_PRIMARY_MONITOR.invokeExact(display);
    }
    if (monitor.equals(MemorySegment.NULL)) {
      monitor = (MemorySegment) DISPLAY_GET_MONITOR.invokeExact(display, 0);
    }
    return monitor;
  }

  /** {@code {x, y, width, height}} of the whole monitor, in the pixels of GDK. */
  @SneakyThrows
  public int[] monitorGeometry(MemorySegment monitor) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rectangle = arena.allocate(RECTANGLE);
      MONITOR_GET_GEOMETRY.invokeExact(monitor, rectangle);
      return Gdk.rectangle(rectangle);
    }
  }

  /** {@code {x, y, width, height}} of the monitor minus panels and docks, where GDK knows them. */
  @SneakyThrows
  public int[] monitorWorkarea(MemorySegment monitor) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rectangle = arena.allocate(RECTANGLE);
      MONITOR_GET_WORKAREA.invokeExact(monitor, rectangle);
      return Gdk.rectangle(rectangle);
    }
  }

  /** The scale factor of the monitor, a whole number. */
  @SneakyThrows
  public int monitorScaleFactor(MemorySegment monitor) {
    return (int) MONITOR_GET_SCALE_FACTOR.invokeExact(monitor);
  }

  /** Whether the monitor is the primary one; Wayland has none. */
  @SneakyThrows
  public boolean isPrimaryMonitor(MemorySegment monitor) {
    return (int) MONITOR_IS_PRIMARY.invokeExact(monitor) != 0;
  }

  /**
   * The manufacturer and the model of the monitor, as far as GDK knows them, or {@code null}.
   * Either can be a code rather than a name, as the EDID of the monitor has it.
   */
  @SneakyThrows
  public String monitorName(MemorySegment monitor) {
    String manufacturer =
        NativeLibraries.string((MemorySegment) MONITOR_GET_MANUFACTURER.invokeExact(monitor));
    String model = NativeLibraries.string((MemorySegment) MONITOR_GET_MODEL.invokeExact(monitor));
    if (manufacturer == null || manufacturer.isBlank()) {
      return model;
    }
    return model == null || model.isBlank() ? manufacturer : manufacturer + " " + model;
  }

  private int[] rectangle(MemorySegment rectangle) {
    return new int[] {
      rectangle.get(Signatures.C_INT, 0),
      rectangle.get(Signatures.C_INT, 4),
      rectangle.get(Signatures.C_INT, 8),
      rectangle.get(Signatures.C_INT, 12)
    };
  }
}
