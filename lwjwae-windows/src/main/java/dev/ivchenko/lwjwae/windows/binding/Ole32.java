package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
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
  private final MethodHandle CO_TASK_MEM_FREE =
      NativeLibraries.downcall(OLE32, "CoTaskMemFree", Signatures.VOID_POINTER);

  /** Enters a single-threaded apartment on the calling thread. */
  @SneakyThrows
  public int coInitializeApartment() {
    return (int) CO_INITIALIZE_EX.invokeExact(MemorySegment.NULL, APARTMENT_THREADED);
  }

  /** Calls {@code CoTaskMemFree}: frees memory that a COM callee allocated for the caller. */
  @SneakyThrows
  public void coTaskMemFree(MemorySegment pointer) {
    CO_TASK_MEM_FREE.invokeExact(pointer);
  }
}
