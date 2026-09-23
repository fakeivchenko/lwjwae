package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

/**
 * A COM object implemented in Java, for the handler interfaces that WebView2 calls back into.
 *
 * <p>Every WebView2 handler has the same shape, {@code IUnknown} plus one {@code Invoke}, and
 * {@code Invoke} has two signatures: {@code (HRESULT, T*)} for completions and {@code (sender,
 * args)} for events. Therefore, the whole backend has exactly two vtables, which every instance
 * shares, and an instance is a 48-byte struct: the vtable pointer, an ID into {@link
 * CallbackRegistry}, a reference count, whether the object is agile, and the IID that it answers
 * to. The ID, not the object address, is the key of the registry. The upcall reads the ID from
 * {@code this}.
 *
 * <p>An agile object also answers to {@code IAgileObject}, which tells the Windows Runtime that it
 * may be called from any thread as it is, with no proxy to marshal the call back to the thread that
 * created it. The toast events need that: they fire on a thread of the pool, and a handler without
 * a proxy/stub pair couldn't be marshaled back at all. The count is atomic for the same reason.
 *
 * <p>Reference counting is real. WebView2 holds a completion handler only until it fires, and an
 * event handler until the view is closed. When the count reaches zero, the entry is dropped, and
 * the memory, in an automatic arena, is freed after it becomes unreachable.
 */
public class ComCallback {
  private static final MemoryLayout OBJECT =
      MemoryLayout.structLayout(
          Signatures.C_POINTER.withName("vtable"),
          Signatures.C_LONG_PTR.withName("id"),
          Signatures.C_LONG_PTR.withName("references"),
          Signatures.C_LONG_PTR.withName("agile"),
          Signatures.GUID.withName("iid"));
  private static final VarHandle VTABLE =
      OBJECT.varHandle(MemoryLayout.PathElement.groupElement("vtable"));
  private static final VarHandle ID = OBJECT.varHandle(MemoryLayout.PathElement.groupElement("id"));
  private static final VarHandle REFERENCES =
      OBJECT.varHandle(MemoryLayout.PathElement.groupElement("references"));
  private static final VarHandle AGILE =
      OBJECT.varHandle(MemoryLayout.PathElement.groupElement("agile"));
  private static final long IID_OFFSET =
      OBJECT.byteOffset(MemoryLayout.PathElement.groupElement("iid"));

  private static final MemorySegment IID_IAGILE_OBJECT =
      Com.guid("94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90");

  private static final CallbackRegistry<ComCallback> OBJECTS = new CallbackRegistry<>();

  private static final MemorySegment QUERY_INTERFACE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          ComCallback.class,
          "queryInterface",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.INT_POINTER_POINTER_POINTER);
  private static final MemorySegment ADD_REF =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          ComCallback.class,
          "addRef",
          MethodType.methodType(int.class, MemorySegment.class),
          Signatures.INT_POINTER);
  private static final MemorySegment RELEASE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          ComCallback.class,
          "release",
          MethodType.methodType(int.class, MemorySegment.class),
          Signatures.INT_POINTER);
  private static final MemorySegment INVOKE_COMPLETION =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          ComCallback.class,
          "invokeCompletion",
          MethodType.methodType(int.class, MemorySegment.class, int.class, MemorySegment.class),
          Signatures.INT_POINTER_INT_POINTER);
  private static final MemorySegment INVOKE_EVENT =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          ComCallback.class,
          "invokeEvent",
          MethodType.methodType(
              int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.INT_POINTER_POINTER_POINTER);

  private static final MemorySegment COMPLETION_VTABLE = vtable(INVOKE_COMPLETION);
  private static final MemorySegment EVENT_VTABLE = vtable(INVOKE_EVENT);

  private final MemorySegment object;
  private final ComCompletion completion;
  private final ComEvent event;

  private ComCallback(
      MemorySegment vtable,
      MemorySegment iid,
      boolean agile,
      ComCompletion completion,
      ComEvent event) {
    this.completion = completion;
    this.event = event;
    this.object = Arena.ofAuto().allocate(OBJECT);
    VTABLE.set(this.object, 0L, vtable);
    ID.set(this.object, 0L, OBJECTS.register(this));
    REFERENCES.set(this.object, 0L, 1L);
    AGILE.set(this.object, 0L, agile ? 1L : 0L);
    MemorySegment.copy(iid, 0L, this.object, IID_OFFSET, Signatures.GUID.byteSize());
  }

  /**
   * Creates a handler that answers to {@code iid} and whose {@code Invoke} is a completion. The
   * caller holds one reference and must {@link Com#release} it after passing the object to
   * WebView2.
   */
  public static MemorySegment completion(MemorySegment iid, ComCompletion handler) {
    return new ComCallback(COMPLETION_VTABLE, iid, false, handler, null).object;
  }

  /**
   * Creates a handler that answers to {@code iid} and whose {@code Invoke} is an event. The same
   * ownership rule as for {@link #completion} applies.
   */
  public static MemorySegment event(MemorySegment iid, ComEvent handler) {
    return new ComCallback(EVENT_VTABLE, iid, false, null, handler).object;
  }

  /**
   * Creates an event handler that is also agile: the Windows Runtime calls it on whatever thread
   * the event fires, rather than on the thread that subscribed. The same ownership rule as for
   * {@link #completion} applies.
   */
  public static MemorySegment agileEvent(MemorySegment iid, ComEvent handler) {
    return new ComCallback(EVENT_VTABLE, iid, true, null, handler).object;
  }

  private static MemorySegment vtable(MemorySegment invoke) {
    MemorySegment vtable = NativeLibraries.ARENA.allocate(Signatures.C_POINTER.byteSize() * 4);
    vtable.setAtIndex(Signatures.C_POINTER, 0, QUERY_INTERFACE);
    vtable.setAtIndex(Signatures.C_POINTER, 1, ADD_REF);
    vtable.setAtIndex(Signatures.C_POINTER, 2, RELEASE);
    vtable.setAtIndex(Signatures.C_POINTER, 3, invoke);
    return vtable;
  }

  private static ComCallback of(MemorySegment self) {
    return OBJECTS.lookup((long) ID.get(self.reinterpret(OBJECT.byteSize()), 0L));
  }

  // --- IUnknown and Invoke, bound by name from the upcall stubs above ---

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * {@code NativeLibraries.upcall} binds by name, so no Java code calls it and the compiler sees a
   * dead private method.
   */
  @SuppressWarnings("unused")
  private static int queryInterface(MemorySegment self, MemorySegment riid, MemorySegment out) {
    try {
      if (out.equals(MemorySegment.NULL)) {
        return Com.E_POINTER;
      }
      MemorySegment object = self.reinterpret(OBJECT.byteSize());
      MemorySegment iid = object.asSlice(IID_OFFSET, Signatures.GUID.byteSize());
      MemorySegment result = out.reinterpret(Signatures.C_POINTER.byteSize());
      boolean agile = (long) AGILE.get(object, 0L) != 0;
      if (Com.sameGuid(riid, Com.IID_IUNKNOWN)
          || Com.sameGuid(riid, iid)
          || (agile && Com.sameGuid(riid, IID_IAGILE_OBJECT))) {
        result.set(Signatures.C_POINTER, 0, self);
        addRef(self);
        return Com.S_OK;
      }
      result.set(Signatures.C_POINTER, 0, MemorySegment.NULL);
      return Com.E_NOINTERFACE;
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      return Com.E_NOINTERFACE;
    }
  }

  /**
   * Suppressed warnings: {@code UnusedReturnValue}: COM requires {@code AddRef} to return the new
   * count for the native caller; the Java callers in this class have no use for it.
   */
  @SuppressWarnings("UnusedReturnValue")
  private static int addRef(MemorySegment self) {
    MemorySegment object = self.reinterpret(OBJECT.byteSize());
    return (int) ((long) REFERENCES.getAndAdd(object, 0L, 1L) + 1);
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * {@code NativeLibraries.upcall} binds by name, so no Java code calls it and the compiler sees a
   * dead private method.
   */
  @SuppressWarnings("unused")
  private static int release(MemorySegment self) {
    MemorySegment object = self.reinterpret(OBJECT.byteSize());
    long references = (long) REFERENCES.getAndAdd(object, 0L, -1L) - 1;
    if (references == 0) {
      OBJECTS.unregister((long) ID.get(object, 0L));
    }
    return (int) references;
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * {@code NativeLibraries.upcall} binds by name, so no Java code calls it and the compiler sees a
   * dead private method.
   */
  @SuppressWarnings("unused")
  private static int invokeCompletion(MemorySegment self, int hresult, MemorySegment result) {
    try {
      ComCallback callback = of(self);
      if (callback != null) {
        callback.completion.invoke(hresult, result);
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return Com.S_OK;
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * {@code NativeLibraries.upcall} binds by name, so no Java code calls it and the compiler sees a
   * dead private method.
   */
  @SuppressWarnings("unused")
  private static int invokeEvent(
      MemorySegment self, MemorySegment sender, MemorySegment arguments) {
    try {
      ComCallback callback = of(self);
      if (callback != null) {
        callback.event.invoke(sender, arguments);
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
    return Com.S_OK;
  }
}
