package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the subset of {@code ole32.dll} that the backend needs: the COM apartment and task
 * memory.
 */
@UtilityClass
public class Ole32 {
  private final SymbolLookup OLE32 = NativeLibraries.load("ole32.dll");

  /** {@code COINIT_APARTMENTTHREADED}: WebView2 requires an STA thread. */
  private final int APARTMENT_THREADED = 0x2;

  private final MethodHandle CO_INITIALIZE_EX =
      NativeLibraries.downcall(OLE32, "CoInitializeEx", Signatures.INT_POINTER_INT);

  /** {@code CLSCTX_INPROC_SERVER}: the object runs in this process. */
  private final int INPROC_SERVER = 0x1;

  private final MethodHandle CO_CREATE_INSTANCE =
      NativeLibraries.downcall(
          OLE32, "CoCreateInstance", Signatures.INT_POINTER_POINTER_INT_POINTER_POINTER);
  private final MethodHandle CO_TASK_MEM_FREE =
      NativeLibraries.downcall(OLE32, "CoTaskMemFree", Signatures.VOID_POINTER);
  private final MethodHandle CREATE_STREAM_ON_HGLOBAL =
      NativeLibraries.downcall(OLE32, "CreateStreamOnHGlobal", Signatures.INT_POINTER_INT_POINTER);
  private final MethodHandle GET_HGLOBAL_FROM_STREAM =
      NativeLibraries.downcall(OLE32, "GetHGlobalFromStream", Signatures.INT_POINTER_POINTER);

  /** Enters a single-threaded apartment on the calling thread. */
  @SneakyThrows
  public int coInitializeApartment() {
    return (int) CO_INITIALIZE_EX.invokeExact(MemorySegment.NULL, APARTMENT_THREADED);
  }

  /**
   * Calls {@code CoCreateInstance} for an object of this process: a new {@code clsid} object, asked
   * for the interface {@code iid}, which the caller releases.
   */
  @SneakyThrows
  public MemorySegment coCreateInstance(MemorySegment clsid, MemorySegment iid) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "CoCreateInstance",
          (int) CO_CREATE_INSTANCE.invokeExact(clsid, MemorySegment.NULL, INPROC_SERVER, iid, out));
      return Com.pointerAt(out);
    }
  }

  /** Calls {@code CoTaskMemFree}: frees memory that a COM callee allocated for the caller. */
  @SneakyThrows
  public void coTaskMemFree(MemorySegment pointer) {
    CO_TASK_MEM_FREE.invokeExact(pointer);
  }

  /**
   * An empty {@code IStream} over global memory that grows as it's written, which the caller
   * releases; the memory goes with it.
   *
   * @throws IllegalStateException If Windows has no memory for it.
   */
  @SneakyThrows
  public MemorySegment memoryStream() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment stream = arena.allocate(Signatures.C_POINTER);
      int hresult = (int) CREATE_STREAM_ON_HGLOBAL.invokeExact(MemorySegment.NULL, 1, stream);
      if (hresult < 0) {
        throw new IllegalStateException(
            "CreateStreamOnHGlobal failed with HRESULT 0x%08X".formatted(hresult));
      }
      return stream.get(Signatures.C_POINTER, 0);
    }
  }

  /** The global memory under a stream of {@link #memoryStream()}, which the stream owns. */
  @SneakyThrows
  public MemorySegment streamMemory(MemorySegment stream) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment memory = arena.allocate(Signatures.C_POINTER);
      int _ = (int) GET_HGLOBAL_FROM_STREAM.invokeExact(stream, memory);
      return memory.get(Signatures.C_POINTER, 0);
    }
  }
}
