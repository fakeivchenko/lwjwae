package dev.ivchenko.lwjwae.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Helpers around the Foreign Function and Memory API that every backend shares.
 *
 * <p>Backends bind native libraries only through this class, which keeps every {@link
 * FunctionDescriptor} and every upcall target enumerable through {@link #downcalls()} and {@link
 * #upcalls()}. {@code native-image} can only compile the stubs that it knew about at build time, so
 * a test compares those sets with the reachability metadata that ships in the backend JAR file.
 */
@UtilityClass
public class NativeLibraries {
  private final Set<FunctionDescriptor> DOWNCALLS =
      Collections.synchronizedSet(new LinkedHashSet<>());
  private final Set<UpcallTarget> UPCALLS = Collections.synchronizedSet(new LinkedHashSet<>());

  /** The C ABI linker of the platform. */
  public final Linker LINKER = Linker.nativeLinker();

  /**
   * The arena that backs library handles and upcall stubs. Both live as long as the process, so a
   * global arena avoids lifetime bookkeeping.
   */
  public final Arena ARENA = Arena.global();

  /**
   * Opens the first library in {@code sonames} that the platform loader accepts.
   *
   * @throws UnsatisfiedLinkError If none of the names loads. The failure of every attempt is
   *     attached as suppressed.
   */
  public SymbolLookup load(String... sonames) {
    UnsatisfiedLinkError failure =
        new UnsatisfiedLinkError("None of " + String.join(", ", sonames) + " could be loaded");
    for (String soname : sonames) {
      try {
        return SymbolLookup.libraryLookup(soname, ARENA);
      } catch (IllegalArgumentException e) {
        failure.addSuppressed(e);
      }
    }
    throw failure;
  }

  /**
   * Checks whether any library in {@code sonames} can be opened. A failure isn't reported as an
   * error.
   */
  public boolean isLoadable(String... sonames) {
    try {
      load(sonames);
      return true;
    } catch (UnsatisfiedLinkError _) {
      return false;
    }
  }

  /**
   * Binds the C function {@code symbol} with the given signature.
   *
   * @throws UnsatisfiedLinkError If the library has no such symbol.
   */
  public MethodHandle downcall(SymbolLookup library, String symbol, FunctionDescriptor descriptor) {
    MemorySegment address =
        library
            .find(symbol)
            .orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + symbol));
    DOWNCALLS.add(descriptor);
    return LINKER.downcallHandle(address, descriptor);
  }

  /**
   * Returns a handle that calls any function of the given signature, with the address of the
   * function as its first argument. COM methods are called this way, because their addresses come
   * from vtables.
   */
  public MethodHandle downcall(FunctionDescriptor descriptor) {
    DOWNCALLS.add(descriptor);
    return LINKER.downcallHandle(descriptor);
  }

  /**
   * Binds a static Java method as a C callback. The stub is allocated in {@link #ARENA}, so every
   * window can share one stub, and the stub can't dangle while native code holds it.
   *
   * @param lookup A lookup created in the class that declares {@code method}, so that private
   *     callbacks stay private.
   * @throws ExceptionInInitializerError If the method doesn't exist or isn't accessible. Bindings
   *     are created in static initializers, and this is the error that a static initializer
   *     reports.
   */
  public MemorySegment upcall(
      MethodHandles.Lookup lookup,
      Class<?> owner,
      String method,
      MethodType type,
      FunctionDescriptor descriptor) {
    try {
      MethodHandle target = lookup.findStatic(owner, method, type);
      UPCALLS.add(new UpcallTarget(owner, method, descriptor));
      return LINKER.upcallStub(target, descriptor, ARENA);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * Returns every distinct signature that was bound with {@link #downcall} so far, in binding
   * order.
   */
  public Set<FunctionDescriptor> downcalls() {
    return Collections.unmodifiableSet(DOWNCALLS);
  }

  /** Returns every method that was exposed with {@link #upcall} so far, in binding order. */
  public Set<UpcallTarget> upcalls() {
    return Collections.unmodifiableSet(UPCALLS);
  }

  /** Reads a NUL-terminated UTF-8 string from a pointer that can be {@code NULL}. */
  public String string(MemorySegment pointer) {
    if (pointer == null || pointer.equals(MemorySegment.NULL)) {
      return null;
    }
    return pointer.reinterpret(Long.MAX_VALUE).getString(0);
  }
}
