package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.ui.EventLoopDispatcher;
import dev.ivchenko.lwjwae.windows.binding.Kernel32;
import dev.ivchenko.lwjwae.windows.binding.Ole32;
import dev.ivchenko.lwjwae.windows.binding.Signatures;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The process-wide Win32 UI thread.
 *
 * <p>WebView2 lives in a single-threaded COM apartment. The thread that creates a controller owns
 * it, receives its callbacks, and must pump messages for them to arrive. This dispatcher is that
 * thread. It enters the apartment, runs {@code GetMessage} and {@code DispatchMessage} for the rest
 * of the process, and drains queued tasks whenever a {@code WM_APP} message posted from another
 * thread lands in its queue.
 *
 * <p>WebView2 creates its objects asynchronously and reports back through that same queue, so a
 * caller that needs the result has to wait while messages keep flowing. Another thread blocks. The
 * UI thread can't, because blocking it would starve the very callback it waits for, so {@link
 * #await} runs a nested message loop there instead, the way a modal dialog does. Everything that
 * the outer loop would have run meanwhile, queued tasks and callbacks of other windows included,
 * runs inside the wait.
 */
public class WindowsDispatcher extends EventLoopDispatcher {
  private static final WindowsDispatcher INSTANCE = new WindowsDispatcher();

  private volatile int threadId;

  private WindowsDispatcher() {
    super("lwjwae-win32");
  }

  /**
   * Returns the dispatcher, and starts the UI thread on first use.
   *
   * @throws IllegalStateException If the thread can't enter a COM apartment.
   */
  public static WindowsDispatcher instance() {
    INSTANCE.start();
    return INSTANCE;
  }

  @Override
  protected void initialize() {
    int hresult = Ole32.coInitializeApartment();
    if (hresult < 0) {
      throw new IllegalStateException(
          "CoInitializeEx failed with HRESULT 0x%08X".formatted(hresult));
    }
    this.threadId = Kernel32.currentThreadId();
    User32.ensureMessageQueue();
  }

  @Override
  protected void runEventLoop() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment message = arena.allocate(Signatures.MSG);
      while (User32.getMessage(message)) {
        this.dispatch(message);
      }
    }
  }

  /**
   * Waits for a result that a WebView2 callback delivers on this thread, from any thread.
   *
   * @return The value of {@code future}.
   * @throws ExecutionException If {@code future} failed.
   * @throws TimeoutException If {@code future} isn't done within {@code timeout}.
   * @throws InterruptedException If the calling thread, other than the UI thread, is interrupted.
   */
  <T> T await(CompletableFuture<T> future, Duration timeout)
      throws InterruptedException, ExecutionException, TimeoutException {
    if (!this.isDispatchThread()) {
      return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }
    long deadline = System.nanoTime() + timeout.toNanos();
    // GetMessage has no timeout; a wake-up at the deadline lets the loop notice it.
    Thread alarm =
        Thread.ofVirtual()
            .name("lwjwae-win32-alarm")
            .start(
                () -> {
                  try {
                    Thread.sleep(timeout);
                    this.wakeUp();
                  } catch (InterruptedException _) {
                    // The wait ended first.
                  }
                });
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment message = arena.allocate(Signatures.MSG);
      while (!future.isDone() && System.nanoTime() - deadline < 0) {
        if (!User32.getMessage(message)) {
          throw new IllegalStateException("The UI thread's message queue was closed");
        }
        this.dispatch(message);
      }
    } finally {
      alarm.interrupt();
    }
    return future.get(0, TimeUnit.NANOSECONDS);
  }

  @Override
  protected void wakeUp() {
    User32.postThreadMessage(this.threadId, User32.WM_APP);
  }

  private void dispatch(MemorySegment message) {
    if (User32.messageId(message) == User32.WM_APP) {
      this.drainTasks();
    } else {
      User32.dispatch(message);
    }
  }
}
