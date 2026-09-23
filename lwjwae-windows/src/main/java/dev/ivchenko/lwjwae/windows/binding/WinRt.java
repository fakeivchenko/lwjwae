package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the Windows Runtime core in {@code combase.dll}: {@code HSTRING}s and the activation
 * of runtime classes.
 *
 * <p>A Windows Runtime object is a COM object whose interfaces start with {@code IInspectable}, so
 * {@link Com} calls it as it calls WebView2: by vtable slot. The first own method of a Windows
 * Runtime interface is slot 6, after the three of {@code IUnknown} and the three of {@code
 * IInspectable}. The COM apartment of the UI thread serves the Windows Runtime as well, because
 * {@code RoInitialize} is {@code CoInitializeEx} under another name.
 */
@UtilityClass
public class WinRt {
  private final SymbolLookup COMBASE = NativeLibraries.load("combase.dll");

  /** The first slot of a Windows Runtime interface of its own, after {@code IInspectable}. */
  public final int FIRST_SLOT = 6;

  private final MethodHandle WINDOWS_CREATE_STRING =
      NativeLibraries.downcall(COMBASE, "WindowsCreateString", Signatures.INT_POINTER_INT_POINTER);
  private final MethodHandle WINDOWS_DELETE_STRING =
      NativeLibraries.downcall(COMBASE, "WindowsDeleteString", Signatures.INT_POINTER);
  private final MethodHandle WINDOWS_GET_STRING_RAW_BUFFER =
      NativeLibraries.downcall(
          COMBASE, "WindowsGetStringRawBuffer", Signatures.POINTER_POINTER_POINTER);
  private final MethodHandle RO_GET_ACTIVATION_FACTORY =
      NativeLibraries.downcall(
          COMBASE, "RoGetActivationFactory", Signatures.INT_POINTER_POINTER_POINTER);
  private final MethodHandle RO_ACTIVATE_INSTANCE =
      NativeLibraries.downcall(COMBASE, "RoActivateInstance", Signatures.INT_POINTER_POINTER);

  /** {@code WindowsCreateString}: an {@code HSTRING} that the caller frees with {@link #delete}. */
  @SneakyThrows
  public MemorySegment create(String text) {
    if (text.isEmpty()) {
      return MemorySegment.NULL;
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "WindowsCreateString",
          (int) WINDOWS_CREATE_STRING.invokeExact(Wide.allocate(arena, text), text.length(), out));
      return Com.pointerAt(out);
    }
  }

  /** {@code WindowsDeleteString}. {@code NULL}, the empty string, is ignored. */
  @SneakyThrows
  public void delete(MemorySegment string) {
    if (!string.equals(MemorySegment.NULL)) {
      int _ = (int) WINDOWS_DELETE_STRING.invokeExact(string);
    }
  }

  /** Reads an {@code HSTRING} that the caller owns, and then frees it. */
  @SneakyThrows
  public String take(MemorySegment string) {
    if (string.equals(MemorySegment.NULL)) {
      return "";
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment length = arena.allocate(Signatures.C_INT);
      MemorySegment buffer =
          (MemorySegment) WINDOWS_GET_STRING_RAW_BUFFER.invokeExact(string, length);
      int characters = length.get(Signatures.C_INT, 0);
      byte[] bytes = buffer.reinterpret(characters * 2L).toArray(ValueLayout.JAVA_BYTE);
      return new String(bytes, StandardCharsets.UTF_16LE);
    } finally {
      delete(string);
    }
  }

  /** {@code RoGetActivationFactory}: the factory or statics interface {@code iid} of a class. */
  @SneakyThrows
  public MemorySegment activationFactory(String className, MemorySegment iid) {
    MemorySegment name = create(className);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "RoGetActivationFactory " + className,
          (int) RO_GET_ACTIVATION_FACTORY.invokeExact(name, iid, out));
      return Com.pointerAt(out);
    } finally {
      delete(name);
    }
  }

  /** {@code RoActivateInstance}: a new instance of a class, as {@code IInspectable}. */
  @SneakyThrows
  public MemorySegment activateInstance(String className) {
    MemorySegment name = create(className);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "RoActivateInstance " + className, (int) RO_ACTIVATE_INSTANCE.invokeExact(name, out));
      return Com.pointerAt(out);
    } finally {
      delete(name);
    }
  }

  /**
   * {@code IUnknown::QueryInterface}: {@code object} as interface {@code iid}, with a reference.
   */
  public MemorySegment query(MemorySegment object, MemorySegment iid) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("QueryInterface", Com.call(object, 0, iid, out));
      return Com.pointerAt(out);
    }
  }
}
