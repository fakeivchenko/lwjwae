package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/** Bindings to {@code shcore.dll}: the density of a monitor. */
@UtilityClass
public class Shcore {
  private final SymbolLookup SHCORE = NativeLibraries.load("shcore.dll");

  private final MethodHandle GET_DPI_FOR_MONITOR =
      NativeLibraries.downcall(
          SHCORE, "GetDpiForMonitor", Signatures.INT_POINTER_INT_POINTER_POINTER);

  /** {@code MDT_EFFECTIVE_DPI}: the density that the display settings give the monitor. */
  private final int EFFECTIVE_DPI = 0;

  /** The density of the standard scale, 100 %. */
  public final int STANDARD_DPI = 96;

  /**
   * The effective density of {@code monitor}, as this process sees it: {@link #STANDARD_DPI} for a
   * process that doesn't declare itself aware of densities, since Windows scales it as a whole.
   */
  @SneakyThrows
  public int dpiForMonitor(MemorySegment monitor) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment x = arena.allocate(Signatures.C_INT);
      MemorySegment y = arena.allocate(Signatures.C_INT);
      int result = (int) GET_DPI_FOR_MONITOR.invokeExact(monitor, EFFECTIVE_DPI, x, y);
      return result < 0 ? STANDARD_DPI : x.get(Signatures.C_INT, 0);
    }
  }
}
