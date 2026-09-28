package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.windows.exception.ComCallFailedException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.UUID;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Calls COM objects through their vtables.
 *
 * <p>A COM object is a pointer to a struct whose first word points to a table of function pointers,
 * and a method call is a C call through slot {@code n} with the object as the first argument.
 * Therefore, each method shape gets one {@link MethodHandle} that takes the function address first,
 * and calling method {@code n} on {@code object} means reading slot {@code n} and invoking it.
 * There's no IDL and there are no proxies. The slot numbers come from the SDK header.
 */
@UtilityClass
public class Com {
  public final int S_OK = 0;
  public final int E_NOINTERFACE = 0x80004002;
  public final int E_POINTER = 0x80004003;

  /** {@code IID_IUnknown}. */
  public final MemorySegment IID_IUNKNOWN = Com.guid("00000000-0000-0000-C000-000000000046");

  private final int ADD_REF = 1;
  private final int RELEASE = 2;

  private final MethodHandle CALL_P = NativeLibraries.downcall(Signatures.INT_POINTER);
  private final MethodHandle CALL_P_I = NativeLibraries.downcall(Signatures.INT_POINTER_INT);
  private final MethodHandle CALL_P_P = NativeLibraries.downcall(Signatures.INT_POINTER_POINTER);
  private final MethodHandle CALL_P_P_P =
      NativeLibraries.downcall(Signatures.INT_POINTER_POINTER_POINTER);
  private final MethodHandle CALL_P_P_I =
      NativeLibraries.downcall(Signatures.INT_POINTER_POINTER_INT);
  private final MethodHandle CALL_P_I_P =
      NativeLibraries.downcall(Signatures.INT_POINTER_INT_POINTER);
  private final MethodHandle CALL_P_L_P =
      NativeLibraries.downcall(Signatures.INT_POINTER_LONG_POINTER);
  private final MethodHandle CALL_P_P_I_P =
      NativeLibraries.downcall(Signatures.INT_POINTER_POINTER_INT_POINTER);
  private final MethodHandle CALL_P_RECT = NativeLibraries.downcall(Signatures.INT_POINTER_RECT);
  private final MethodHandle CALL_P_P_I_P_P_P =
      NativeLibraries.downcall(Signatures.INT_POINTER_POINTER_INT_POINTER_X3);

  /** The function pointer in vtable slot {@code index} of {@code object}. */
  public MemorySegment slot(MemorySegment object, int index) {
    MemorySegment vtable =
        object.reinterpret(Signatures.C_POINTER.byteSize()).get(Signatures.C_POINTER, 0);
    return vtable
        .reinterpret((index + 1L) * Signatures.C_POINTER.byteSize())
        .getAtIndex(Signatures.C_POINTER, index);
  }

  /** Calls method {@code index} of {@code object} with a 32-bit integer and a pointer. */
  @SneakyThrows
  public int call(MemorySegment object, int index, int first, MemorySegment second) {
    return (int) CALL_P_I_P.invokeExact(Com.slot(object, index), object, first, second);
  }

  /** Calls method {@code index} of {@code object} with no arguments. */
  @SneakyThrows
  public int call(MemorySegment object, int index) {
    return (int) CALL_P.invokeExact(Com.slot(object, index), object);
  }

  /** Calls method {@code index} of {@code object} with one integer argument. */
  @SneakyThrows
  public int call(MemorySegment object, int index, int argument) {
    return (int) CALL_P_I.invokeExact(Com.slot(object, index), object, argument);
  }

  /** Calls method {@code index} of {@code object} with one pointer argument. */
  @SneakyThrows
  public int call(MemorySegment object, int index, MemorySegment argument) {
    return (int) CALL_P_P.invokeExact(Com.slot(object, index), object, argument);
  }

  /** Calls method {@code index} of {@code object} with two pointer arguments. */
  @SneakyThrows
  public int call(MemorySegment object, int index, MemorySegment first, MemorySegment second) {
    return (int) CALL_P_P_P.invokeExact(Com.slot(object, index), object, first, second);
  }

  /** Calls method {@code index} of {@code object} with a pointer and an integer. */
  @SneakyThrows
  public int call(MemorySegment object, int index, MemorySegment first, int second) {
    return (int) CALL_P_P_I.invokeExact(Com.slot(object, index), object, first, second);
  }

  /** Calls method {@code index} of {@code object} with a 64-bit integer and a pointer. */
  @SneakyThrows
  public int call(MemorySegment object, int index, long first, MemorySegment second) {
    return (int) CALL_P_L_P.invokeExact(Com.slot(object, index), object, first, second);
  }

  /** Calls method {@code index} of {@code object} with a pointer, an integer, and a pointer. */
  @SneakyThrows
  public int call(
      MemorySegment object, int index, MemorySegment first, int second, MemorySegment third) {
    return (int) CALL_P_P_I_P.invokeExact(Com.slot(object, index), object, first, second, third);
  }

  /** The shape of {@code ICoreWebView2Environment::CreateWebResourceResponse}. */
  @SneakyThrows
  public int call(
      MemorySegment object,
      int index,
      MemorySegment stream,
      int status,
      MemorySegment reason,
      MemorySegment headers,
      MemorySegment out) {
    return (int)
        CALL_P_P_I_P_P_P.invokeExact(
            Com.slot(object, index), object, stream, status, reason, headers, out);
  }

  /** Calls method {@code index} of {@code object} with a {@code RECT} passed by value. */
  @SneakyThrows
  public int callWithRect(MemorySegment object, int index, MemorySegment rect) {
    return (int) CALL_P_RECT.invokeExact(Com.slot(object, index), object, rect);
  }

  /** Calls {@code IUnknown::AddRef}. */
  public void addRef(MemorySegment object) {
    Com.call(object, ADD_REF);
  }

  /** Calls {@code IUnknown::Release}. {@code NULL} and {@code null} are ignored. */
  public void release(MemorySegment object) {
    if (object != null && !object.equals(MemorySegment.NULL)) {
      Com.call(object, RELEASE);
    }
  }

  /** Throws when {@code hresult} is a failure code. */
  public void check(String call, int hresult) {
    if (hresult < 0) {
      throw new ComCallFailedException(call, hresult);
    }
  }

  /** Reads an {@code [out] T**} slot. */
  public MemorySegment pointerAt(MemorySegment out) {
    return out.get(Signatures.C_POINTER, 0);
  }

  /**
   * A {@code GUID} in its binary layout: the first three fields little-endian, and the last eight
   * bytes as written. This is why the textual form can't be copied directly.
   */
  public MemorySegment guid(String text) {
    UUID uuid = UUID.fromString(text);
    MemorySegment guid = NativeLibraries.ARENA.allocate(Signatures.GUID);
    long high = uuid.getMostSignificantBits();
    final long low = uuid.getLeastSignificantBits();
    guid.set(Signatures.C_INT, 0, (int) (high >>> 32));
    guid.set(Signatures.C_SHORT, 4, (short) (high >>> 16));
    guid.set(Signatures.C_SHORT, 6, (short) high);
    for (int i = 0; i < 8; i++) {
      guid.set(ValueLayout.JAVA_BYTE, 8 + i, (byte) (low >>> (56 - 8 * i)));
    }
    return guid;
  }

  /** Whether two 16-byte GUIDs are equal. */
  public boolean sameGuid(MemorySegment first, MemorySegment second) {
    return first.reinterpret(16).mismatch(second.reinterpret(16)) == -1;
  }
}
