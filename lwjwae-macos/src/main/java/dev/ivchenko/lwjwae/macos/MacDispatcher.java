package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.macos.binding.MethodStub;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.macos.binding.Signatures;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * The main thread of the process, which is the only thread that AppKit accepts, and one that the
 * library doesn't own.
 *
 * <p>There are two situations. Under the {@code java} launcher, the main thread is parked in a
 * {@code CFRunLoop} while Java code runs on another thread. Work is handed over with {@code
 * performSelectorOnMainThread:}, a run loop source, so it keeps firing after the first batch has
 * started {@code -[NSApplication run]} and that nested loop owns the thread for the rest of the
 * process. In a native image, the {@code main} method of the application is the main thread. Calls
 * made from it run inline, and {@link MacApplication#run()} starts the application loop.
 */
public class MacDispatcher extends UiDispatcher {
  private static final MacDispatcher INSTANCE = new MacDispatcher();
  private static final MemorySegment DRAIN_STUB =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacDispatcher.class,
          "drain",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.DELEGATE_0);
  private static final MemorySegment TARGET =
      ObjC.send(
          ObjC.send(
              ObjC.defineClass(
                  "LwjwaeDispatcher",
                  ObjC.cls("NSObject"),
                  Map.of("drain", new MethodStub(DRAIN_STUB, "v@:"))),
              "alloc"),
          "init");

  private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

  private volatile boolean started;
  private volatile boolean applicationRunning;

  private MacDispatcher() {}

  /** Returns the dispatcher. The application object is created on first use. */
  public static MacDispatcher instance() {
    INSTANCE.start();
    return INSTANCE;
  }

  @Override
  public boolean isDispatchThread() {
    return ObjC.isMainThread();
  }

  @Override
  public void post(Runnable task) {
    this.tasks.add(task);
    ObjC.performOnMainThread(TARGET, "drain");
  }

  @Override
  protected <T> T execute(Supplier<T> action) {
    MemorySegment pool = ObjC.autoreleasePoolPush();
    try {
      return action.get();
    } finally {
      ObjC.autoreleasePoolPop(pool);
    }
  }

  /** Checks whether {@code -[NSApplication run]} is already looping on the main thread. */
  boolean isApplicationRunning() {
    return this.applicationRunning;
  }

  /** Runs the application loop on the calling main thread until {@link AppKit#stopRunLoop()}. */
  void runApplication() {
    this.applicationRunning = true;
    try {
      AppKit.run();
    } finally {
      this.applicationRunning = false;
    }
  }

  private synchronized void start() {
    if (this.started) {
      return;
    }
    this.started = true;
    if (this.isDispatchThread()) {
      this.execute(
          () -> {
            AppKit.finishLaunching();
            return null;
          });
      return;
    }
    this.post(AppKit::application);
    this.post(this::runApplication);
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void drain(MemorySegment self, MemorySegment command) {
    for (Runnable task = INSTANCE.tasks.poll(); task != null; task = INSTANCE.tasks.poll()) {
      INSTANCE.runReported(task);
    }
  }

  private void runReported(Runnable task) {
    try {
      this.execute(
          () -> {
            task.run();
            return null;
          });
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
