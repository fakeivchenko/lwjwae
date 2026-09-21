package dev.ivchenko.lwjwae.macos.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * The Objective-C runtime and the block ABI: everything that Cocoa needs and that isn't a message
 * send to a particular class.
 *
 * <p>Messages go through {@code objc_msgSend}, with one handle per signature. The {@code send*}
 * methods name the shape, and the caller names the selector. Selectors and classes are looked up
 * once and cached, so a send costs one hash lookup and one native call.
 */
@UtilityClass
public class ObjC {
  private final SymbolLookup OBJC = NativeLibraries.load("libobjc.A.dylib");
  private final SymbolLookup SYSTEM = NativeLibraries.load("libSystem.B.dylib");

  private final MethodHandle GET_CLASS =
      NativeLibraries.downcall(OBJC, "objc_getClass", Signatures.POINTER_POINTER);
  private final MethodHandle REGISTER_NAME =
      NativeLibraries.downcall(OBJC, "sel_registerName", Signatures.POINTER_POINTER);
  private final MethodHandle ALLOCATE_CLASS_PAIR =
      NativeLibraries.downcall(
          OBJC, "objc_allocateClassPair", Signatures.POINTER_POINTER_POINTER_LONG);
  private final MethodHandle REGISTER_CLASS_PAIR =
      NativeLibraries.downcall(OBJC, "objc_registerClassPair", Signatures.VOID_POINTER);
  private final MethodHandle CLASS_ADD_METHOD =
      NativeLibraries.downcall(
          OBJC, "class_addMethod", Signatures.BOOL_POINTER_POINTER_POINTER_POINTER);
  private final MethodHandle POOL_PUSH =
      NativeLibraries.downcall(OBJC, "objc_autoreleasePoolPush", Signatures.POINTER_VOID);
  private final MethodHandle POOL_POP =
      NativeLibraries.downcall(OBJC, "objc_autoreleasePoolPop", Signatures.VOID_POINTER);
  private final MethodHandle PTHREAD_MAIN_NP =
      NativeLibraries.downcall(SYSTEM, "pthread_main_np", Signatures.INT_VOID);

  private final MemorySegment GLOBAL_BLOCK_ISA = SYSTEM.findOrThrow("_NSConcreteGlobalBlock");
  private final int BLOCK_IS_GLOBAL = 1 << 28;
  private final VarHandle BLOCK_CONTEXT =
      Signatures.BLOCK.varHandle(MemoryLayout.PathElement.groupElement("context"));
  private final MemorySegment BLOCK_DESCRIPTOR = blockDescriptor();

  private final MethodHandle MSG_ID =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_ID);
  private final MethodHandle MSG_VOID =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_VOID);
  private final MethodHandle MSG_LONG =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_LONG);
  private final MethodHandle MSG_ID_ID =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_ID_ID);
  private final MethodHandle MSG_ID_BOOL =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_ID_BOOL);
  private final MethodHandle MSG_VOID_ID =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_VOID_ID);
  private final MethodHandle MSG_VOID_LONG =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_VOID_LONG);
  private final MethodHandle MSG_VOID_BOOL =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_VOID_BOOL);
  private final MethodHandle MSG_BOOL_LONG =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_BOOL_LONG);
  private final MethodHandle MSG_ID_ID_ID =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_ID_ID_ID);
  private final MethodHandle MSG_VOID_ID_ID =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_VOID_ID_ID);
  private final MethodHandle MSG_VOID_ID_BOOL =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_VOID_ID_BOOL);
  private final MethodHandle MSG_VOID_SEL_ID_BOOL =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_VOID_SEL_ID_BOOL);
  private final MethodHandle MSG_ID_POINTER_LONG =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_ID_POINTER_LONG);
  private final MethodHandle MSG_VOID_POINTER_LONG =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_VOID_POINTER_LONG);
  private final MethodHandle MSG_ID_ID_LONG_BOOL =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_ID_ID_LONG_BOOL);
  private final MethodHandle MSG_ID_ID_LONG_ID =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_ID_ID_LONG_ID);
  private final MethodHandle MSG_ID_ID_ID_LONG_ID =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_ID_ID_ID_LONG_ID);
  private final MethodHandle MSG_ID_RECT_LONG_LONG_BOOL =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_ID_RECT_LONG_LONG_BOOL);
  private final MethodHandle MSG_ID_RECT_ID =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_ID_RECT_ID);
  private final MethodHandle MSG_VOID_SIZE =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_VOID_SIZE);
  private final MethodHandle MSG_OTHER_EVENT =
      NativeLibraries.downcall(OBJC, "objc_msgSend", Signatures.MSG_OTHER_EVENT);

  private final Map<String, MemorySegment> CLASSES = new ConcurrentHashMap<>();
  private final Map<String, MemorySegment> SELECTORS = new ConcurrentHashMap<>();

  // --- runtime ---

  /** {@code objc_getClass}. The framework that defines the class must already be loaded. */
  public MemorySegment cls(String name) {
    return CLASSES.computeIfAbsent(name, ObjC::lookupClass);
  }

  /** {@code sel_registerName}. */
  public MemorySegment sel(String name) {
    return SELECTORS.computeIfAbsent(name, ObjC::registerSelector);
  }

  /** Creates and registers a subclass of {@code superclass} whose methods are Java upcall stubs. */
  @SneakyThrows
  public MemorySegment defineClass(
      String name, MemorySegment superclass, Map<String, MethodStub> methods) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment cls =
          (MemorySegment) ALLOCATE_CLASS_PAIR.invokeExact(superclass, arena.allocateFrom(name), 0L);
      if (cls.equals(MemorySegment.NULL)) {
        throw new IllegalStateException("Could not define class " + name);
      }
      for (Map.Entry<String, MethodStub> method : methods.entrySet()) {
        boolean added =
            (boolean)
                CLASS_ADD_METHOD.invokeExact(
                    cls,
                    sel(method.getKey()),
                    method.getValue().implementation(),
                    arena.allocateFrom(method.getValue().typeEncoding()));
        if (!added) {
          throw new IllegalStateException("Could not add " + method.getKey() + " to " + name);
        }
      }
      REGISTER_CLASS_PAIR.invokeExact(cls);
      return cls;
    }
  }

  /** {@code objc_autoreleasePoolPush}. */
  @SneakyThrows
  public MemorySegment autoreleasePoolPush() {
    return (MemorySegment) POOL_PUSH.invokeExact();
  }

  /** {@code objc_autoreleasePoolPop}. */
  @SneakyThrows
  public void autoreleasePoolPop(MemorySegment pool) {
    POOL_POP.invokeExact(pool);
  }

  /**
   * Whether the calling thread is the main thread of the process, the only thread that AppKit
   * accepts.
   */
  @SneakyThrows
  public boolean isMainThread() {
    return (int) PTHREAD_MAIN_NP.invokeExact() != 0;
  }

  /**
   * {@code performSelectorOnMainThread:withObject:waitUntilDone:NO}: a run loop source on the main
   * thread, which fires from whichever loop that thread is in, either the parked loop of the
   * launcher or a nested {@code -[NSApplication run]}.
   */
  @SneakyThrows
  public void performOnMainThread(MemorySegment receiver, String selector) {
    MSG_VOID_SEL_ID_BOOL.invokeExact(
        receiver,
        sel("performSelectorOnMainThread:withObject:waitUntilDone:"),
        sel(selector),
        MemorySegment.NULL,
        false);
  }

  /**
   * A block literal that calls {@code invoke}, with {@code context} readable through {@link
   * #blockContext}.
   *
   * <p>The block is marked global, so the runtime never copies or frees it. {@code Block_copy}
   * returns the same pointer, which lets the caller own the memory and free it after the block has
   * done its work.
   */
  public MemorySegment block(Arena arena, MemorySegment invoke, long context) {
    MemorySegment block = arena.allocate(Signatures.BLOCK);
    block.set(Signatures.C_POINTER, 0, GLOBAL_BLOCK_ISA);
    block.set(Signatures.C_INT, 8, BLOCK_IS_GLOBAL);
    block.set(Signatures.C_POINTER, 16, invoke);
    block.set(Signatures.C_POINTER, 24, BLOCK_DESCRIPTOR);
    BLOCK_CONTEXT.set(block, 0L, context);
    return block;
  }

  /**
   * The context that a block created by {@link #block} carries. {@code block} is the pointer that
   * the runtime passed.
   */
  public long blockContext(MemorySegment block) {
    return (long) BLOCK_CONTEXT.get(block.reinterpret(Signatures.BLOCK.byteSize()), 0L);
  }

  // --- message sends ---

  /** Sends {@code selector} to {@code receiver}: {@code id -[receiver selector]}. */
  @SneakyThrows
  public MemorySegment send(MemorySegment receiver, String selector) {
    return (MemorySegment) MSG_ID.invokeExact(receiver, sel(selector));
  }

  /** Sends {@code selector} to {@code receiver}: {@code id -[receiver selector:BOOL]}. */
  @SneakyThrows
  public MemorySegment send(MemorySegment receiver, String selector, boolean argument) {
    return (MemorySegment) MSG_ID_BOOL.invokeExact(receiver, sel(selector), argument);
  }

  /** Sends {@code selector} to {@code receiver}: {@code id -[receiver selector:id]}. */
  @SneakyThrows
  public MemorySegment send(MemorySegment receiver, String selector, MemorySegment argument) {
    return (MemorySegment) MSG_ID_ID.invokeExact(receiver, sel(selector), argument);
  }

  /** Sends {@code selector} to {@code receiver}: {@code id -[receiver selector:id:id]}. */
  @SneakyThrows
  public MemorySegment send(
      MemorySegment receiver, String selector, MemorySegment first, MemorySegment second) {
    return (MemorySegment) MSG_ID_ID_ID.invokeExact(receiver, sel(selector), first, second);
  }

  /** Sends {@code selector} to {@code receiver}: {@code id -[receiver selector:id:NSInteger]}. */
  @SneakyThrows
  public MemorySegment send(
      MemorySegment receiver, String selector, MemorySegment bytes, long length) {
    return (MemorySegment) MSG_ID_POINTER_LONG.invokeExact(receiver, sel(selector), bytes, length);
  }

  /**
   * Sends {@code selector} to {@code receiver}: {@code id -[receiver selector:id:NSInteger:BOOL]}.
   */
  @SneakyThrows
  public MemorySegment send(
      MemorySegment receiver, String selector, MemorySegment first, long second, boolean third) {
    return (MemorySegment)
        MSG_ID_ID_LONG_BOOL.invokeExact(receiver, sel(selector), first, second, third);
  }

  /**
   * Sends {@code selector} to {@code receiver}: {@code id -[receiver selector:id:NSInteger:id]}.
   */
  @SneakyThrows
  public MemorySegment send(
      MemorySegment receiver,
      String selector,
      MemorySegment first,
      long second,
      MemorySegment third) {
    return (MemorySegment)
        MSG_ID_ID_LONG_ID.invokeExact(receiver, sel(selector), first, second, third);
  }

  /**
   * Sends {@code selector} to {@code receiver}: {@code id -[receiver selector:id:id:NSInteger:id]}.
   */
  @SneakyThrows
  public MemorySegment send(
      MemorySegment receiver,
      String selector,
      MemorySegment first,
      MemorySegment second,
      long third,
      MemorySegment fourth) {
    return (MemorySegment)
        MSG_ID_ID_ID_LONG_ID.invokeExact(receiver, sel(selector), first, second, third, fourth);
  }

  /**
   * Sends {@code selector} to {@code receiver}: {@code id -[receiver
   * selector:id:NSInteger:NSInteger:BOOL]}.
   */
  @SneakyThrows
  public MemorySegment sendWithRect(
      MemorySegment receiver,
      String selector,
      MemorySegment rect,
      long styleMask,
      long backing,
      boolean defer) {
    return (MemorySegment)
        MSG_ID_RECT_LONG_LONG_BOOL.invokeExact(
            receiver, sel(selector), rect, styleMask, backing, defer);
  }

  /** Sends {@code selector} to {@code receiver}: {@code id -[receiver selector:id:id]}. */
  @SneakyThrows
  public MemorySegment sendWithRect(
      MemorySegment receiver, String selector, MemorySegment rect, MemorySegment argument) {
    return (MemorySegment) MSG_ID_RECT_ID.invokeExact(receiver, sel(selector), rect, argument);
  }

  /** Sends {@code selector} to {@code receiver}: {@code void -[receiver selector]}. */
  @SneakyThrows
  public void sendVoid(MemorySegment receiver, String selector) {
    MSG_VOID.invokeExact(receiver, sel(selector));
  }

  /** Sends {@code selector} to {@code receiver}: {@code void -[receiver selector:id]}. */
  @SneakyThrows
  public void sendVoid(MemorySegment receiver, String selector, MemorySegment argument) {
    MSG_VOID_ID.invokeExact(receiver, sel(selector), argument);
  }

  /** Sends {@code selector} to {@code receiver}: {@code void -[receiver selector:id:id]}. */
  @SneakyThrows
  public void sendVoid(
      MemorySegment receiver, String selector, MemorySegment first, MemorySegment second) {
    MSG_VOID_ID_ID.invokeExact(receiver, sel(selector), first, second);
  }

  /** Sends {@code selector} to {@code receiver}: {@code void -[receiver selector:id:BOOL]}. */
  @SneakyThrows
  public void sendVoid(
      MemorySegment receiver, String selector, MemorySegment first, boolean second) {
    MSG_VOID_ID_BOOL.invokeExact(receiver, sel(selector), first, second);
  }

  /** Sends {@code selector} to {@code receiver}: {@code void -[receiver selector:id:NSInteger]}. */
  @SneakyThrows
  public void sendVoid(MemorySegment receiver, String selector, MemorySegment buffer, long length) {
    MSG_VOID_POINTER_LONG.invokeExact(receiver, sel(selector), buffer, length);
  }

  /** Sends {@code selector} to {@code receiver}: {@code void -[receiver selector:NSInteger]}. */
  @SneakyThrows
  public void sendVoid(MemorySegment receiver, String selector, long argument) {
    MSG_VOID_LONG.invokeExact(receiver, sel(selector), argument);
  }

  /** Sends {@code selector} to {@code receiver}: {@code void -[receiver selector:BOOL]}. */
  @SneakyThrows
  public void sendVoid(MemorySegment receiver, String selector, boolean argument) {
    MSG_VOID_BOOL.invokeExact(receiver, sel(selector), argument);
  }

  /** Sends {@code selector} to {@code receiver}: {@code void -[receiver selector:id]}. */
  @SneakyThrows
  public void sendVoidSize(MemorySegment receiver, String selector, MemorySegment size) {
    MSG_VOID_SIZE.invokeExact(receiver, sel(selector), size);
  }

  /** Sends {@code selector} to {@code receiver}: {@code NSInteger -[receiver selector]}. */
  @SneakyThrows
  public long sendLong(MemorySegment receiver, String selector) {
    return (long) MSG_LONG.invokeExact(receiver, sel(selector));
  }

  /** Sends {@code selector} to {@code receiver}: {@code BOOL -[receiver selector:NSInteger]}. */
  @SneakyThrows
  public boolean sendBool(MemorySegment receiver, String selector, long argument) {
    return (boolean) MSG_BOOL_LONG.invokeExact(receiver, sel(selector), argument);
  }

  /** The one eleven-argument send in the backend. See {@link AppKit#stopRunLoop}. */
  @SneakyThrows
  public MemorySegment sendOtherEvent(
      MemorySegment receiver,
      String selector,
      long type,
      MemorySegment location,
      long modifierFlags,
      double timestamp,
      long windowNumber,
      MemorySegment context,
      short subtype,
      long data1,
      long data2) {
    return (MemorySegment)
        MSG_OTHER_EVENT.invokeExact(
            receiver,
            sel(selector),
            type,
            location,
            modifierFlags,
            timestamp,
            windowNumber,
            context,
            subtype,
            data1,
            data2);
  }

  // --- helpers ---

  /** Checks whether {@code object} is {@code nil}, in either of its two Java spellings. */
  public boolean isNull(MemorySegment object) {
    return object == null || object.equals(MemorySegment.NULL);
  }

  @SneakyThrows
  private MemorySegment lookupClass(String name) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment cls = (MemorySegment) GET_CLASS.invokeExact(arena.allocateFrom(name));
      if (cls.equals(MemorySegment.NULL)) {
        throw new IllegalStateException("No Objective-C class " + name);
      }
      return cls;
    }
  }

  @SneakyThrows
  private MemorySegment registerSelector(String name) {
    try (Arena arena = Arena.ofConfined()) {
      return (MemorySegment) REGISTER_NAME.invokeExact(arena.allocateFrom(name));
    }
  }

  private MemorySegment blockDescriptor() {
    MemorySegment descriptor = NativeLibraries.ARENA.allocate(Signatures.BLOCK_DESCRIPTOR);
    descriptor.set(Signatures.C_LONG, 8, Signatures.BLOCK.byteSize());
    return descriptor;
  }
}
