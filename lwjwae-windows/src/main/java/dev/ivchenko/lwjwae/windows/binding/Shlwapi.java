package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/** Bindings to {@code shlwapi.dll}: an in-memory {@code IStream} for resource responses. */
@UtilityClass
public class Shlwapi {
  private final SymbolLookup SHLWAPI = NativeLibraries.load("shlwapi.dll");

  private final MethodHandle SH_CREATE_MEM_STREAM =
      NativeLibraries.downcall(SHLWAPI, "SHCreateMemStream", Signatures.POINTER_POINTER_INT);

  /** An {@code IStream} over a copy of {@code data}. The caller owns one reference. */
  @SneakyThrows
  public MemorySegment memoryStream(byte[] data) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate(Math.max(data.length, 1));
      MemorySegment.copy(MemorySegment.ofArray(data), 0L, buffer, 0L, data.length);
      return (MemorySegment) SH_CREATE_MEM_STREAM.invokeExact(buffer, data.length);
    }
  }
}
