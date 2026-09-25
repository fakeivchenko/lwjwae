package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/** Bindings to the subset of {@code kernel32.dll} that the backend needs. */
@UtilityClass
public class Kernel32 {
  private final SymbolLookup KERNEL32 = NativeLibraries.load("kernel32.dll");

  /** {@code LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS}. */
  private final int SEARCH_DLL_DIR_AND_DEFAULTS = 0x00000100 | 0x00001000;

  private final MethodHandle GET_MODULE_HANDLE =
      NativeLibraries.downcall(KERNEL32, "GetModuleHandleW", Signatures.POINTER_POINTER);
  private final MethodHandle GET_CURRENT_THREAD_ID =
      NativeLibraries.downcall(KERNEL32, "GetCurrentThreadId", Signatures.INT_VOID);
  private final MethodHandle GET_LAST_ERROR =
      NativeLibraries.downcall(KERNEL32, "GetLastError", Signatures.INT_VOID);
  private final MethodHandle LOAD_LIBRARY_EX =
      NativeLibraries.downcall(KERNEL32, "LoadLibraryExW", Signatures.POINTER_POINTER_POINTER_INT);
  private final MethodHandle GET_PROC_ADDRESS =
      NativeLibraries.downcall(KERNEL32, "GetProcAddress", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle GLOBAL_ALLOC =
      NativeLibraries.downcall(KERNEL32, "GlobalAlloc", Signatures.POINTER_INT_LONG);
  private final MethodHandle GLOBAL_LOCK =
      NativeLibraries.downcall(KERNEL32, "GlobalLock", Signatures.POINTER_POINTER);
  private final MethodHandle GLOBAL_UNLOCK =
      NativeLibraries.downcall(KERNEL32, "GlobalUnlock", Signatures.INT_POINTER);
  private final MethodHandle GLOBAL_FREE =
      NativeLibraries.downcall(KERNEL32, "GlobalFree", Signatures.POINTER_POINTER);

  /** {@code GMEM_MOVEABLE}: the kind of memory that the clipboard takes. */
  private final int GMEM_MOVEABLE = 0x0002;

  /** {@code GetModuleHandleW(NULL)}: the instance handle of the executable. */
  @SneakyThrows
  public MemorySegment moduleHandle() {
    return (MemorySegment) GET_MODULE_HANDLE.invokeExact(MemorySegment.NULL);
  }

  /** Calls {@code GetCurrentThreadId}. */
  @SneakyThrows
  public int currentThreadId() {
    return (int) GET_CURRENT_THREAD_ID.invokeExact();
  }

  /** Calls {@code GetLastError}: the code of the last failed Win32 call on this thread. */
  @SneakyThrows
  public int lastError() {
    return (int) GET_LAST_ERROR.invokeExact();
  }

  /**
   * Loads a DLL by its full path, with its own directory on the search path, so that its private
   * dependencies resolve. A plain {@code LoadLibrary} wouldn't resolve them.
   */
  @SneakyThrows
  public MemorySegment loadLibrary(String path) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment module =
          (MemorySegment)
              LOAD_LIBRARY_EX.invokeExact(
                  Wide.allocate(arena, path), MemorySegment.NULL, SEARCH_DLL_DIR_AND_DEFAULTS);
      if (module.equals(MemorySegment.NULL)) {
        throw new UnsatisfiedLinkError(
            "LoadLibraryExW(" + path + ") failed, error " + Kernel32.lastError());
      }
      return module;
    }
  }

  /** A {@link SymbolLookup} over a module from {@link #loadLibrary}. */
  public SymbolLookup symbols(MemorySegment module) {
    return name -> {
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment address =
            (MemorySegment) GET_PROC_ADDRESS.invokeExact(module, arena.allocateFrom(name));
        return address.equals(MemorySegment.NULL) ? Optional.empty() : Optional.of(address);
      } catch (Throwable t) {
        throw new IllegalStateException(t);
      }
    };
  }

  /**
   * A copy of {@code text} as NUL-terminated UTF-16 in movable global memory, the form that {@code
   * SetClipboardData} takes; the caller hands it over or frees it with {@link #globalFree}.
   *
   * @throws IllegalStateException If Windows has no memory for it.
   */
  @SneakyThrows
  public MemorySegment globalText(String text) {
    byte[] characters = text.getBytes(StandardCharsets.UTF_16LE);
    // Two zero bytes more: the NUL that ends a UTF-16 string.
    byte[] bytes = Arrays.copyOf(characters, characters.length + 2);
    MemorySegment memory =
        (MemorySegment) GLOBAL_ALLOC.invokeExact(GMEM_MOVEABLE, (long) bytes.length);
    if (memory.equals(MemorySegment.NULL)) {
      throw new IllegalStateException("GlobalAlloc failed: " + Kernel32.lastError());
    }
    MemorySegment locked = (MemorySegment) GLOBAL_LOCK.invokeExact(memory);
    MemorySegment.copy(
        MemorySegment.ofArray(bytes), 0, locked.reinterpret(bytes.length), 0, bytes.length);
    int _ = (int) GLOBAL_UNLOCK.invokeExact(memory);
    return memory;
  }

  /** The NUL-terminated UTF-16 text in global memory that another owner holds. */
  @SneakyThrows
  public String readGlobalText(MemorySegment memory) {
    MemorySegment locked = (MemorySegment) GLOBAL_LOCK.invokeExact(memory);
    if (locked.equals(MemorySegment.NULL)) {
      return null;
    }
    try {
      return Wide.read(locked);
    } finally {
      int _ = (int) GLOBAL_UNLOCK.invokeExact(memory);
    }
  }

  /** Calls {@code GlobalFree}. */
  @SneakyThrows
  public void globalFree(MemorySegment memory) {
    MemorySegment _ = (MemorySegment) GLOBAL_FREE.invokeExact(memory);
  }
}
