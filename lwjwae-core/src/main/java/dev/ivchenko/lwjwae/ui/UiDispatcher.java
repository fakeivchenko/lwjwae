package dev.ivchenko.lwjwae.ui;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The single thread that a backend uses its toolkit from, and the way that work from other threads
 * reaches it.
 *
 * <p>Native UI toolkits are single-threaded: the toolkit can only be used from one thread. Which
 * thread that is differs. GTK and Win32 accept any thread as long as it stays the same one, so
 * {@link EventLoopDispatcher} starts its own. Cocoa requires the main thread of the process, which
 * the library doesn't own. This class is the part that every backend shares: identify the thread,
 * hand it work, and run a call inline when already on it.
 */
public abstract class UiDispatcher {
  /**
   * How long {@link #call} waits for the UI thread. Nothing that a window does takes anywhere near
   * this long. A UI thread that stays silent for a minute is stuck, and an exception reports that
   * where a hang wouldn't.
   */
  private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

  private static final int WAITING = 0;
  private static final int STARTED = 1;
  private static final int ABANDONED = 2;

  /**
   * Checks whether the calling thread is the UI thread. Calls from the UI thread must not block on
   * it.
   */
  public abstract boolean isDispatchThread();

  /** Runs {@code task} on the UI thread without waiting for it. */
  public abstract void post(Runnable task);

  /**
   * Runs one unit of work on the UI thread. The default implementation only calls the work. A
   * toolkit that needs something around every call overrides this method. For example, Cocoa needs
   * an autorelease pool.
   */
  protected <T> T execute(Supplier<T> action) {
    return action.get();
  }

  /** Runs {@code task} on the UI thread and waits for it to finish. */
  public final void run(Runnable task) {
    this.call(
        () -> {
          task.run();
          return null;
        });
  }

  /**
   * Runs {@code action} on the UI thread and returns its result. Failures surface with their
   * original type. Calls made from the UI thread itself run inline instead of deadlocking.
   *
   * @throws IllegalStateException If the UI thread doesn't take the action within a minute, or the
   *     wait is interrupted before it does; the action then never runs.
   */
  public final <T> T call(Supplier<T> action) {
    if (this.isDispatchThread()) {
      return this.execute(action);
    }

    CompletableFuture<T> result = new CompletableFuture<>();
    // WAITING until the UI thread takes the action, or ABANDONED once the caller stops waiting:
    // an action that the caller was told failed must not run after all.
    AtomicInteger state = new AtomicInteger(WAITING);
    this.post(
        () -> {
          if (!state.compareAndSet(WAITING, STARTED)) {
            return;
          }
          try {
            result.complete(this.execute(action));
          } catch (Throwable t) {
            result.completeExceptionally(t);
          }
        });
    try {
      try {
        return result.get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        if (state.compareAndSet(WAITING, ABANDONED)) {
          throw e;
        }
        // Started by now: the UI thread is on it, so its result is the answer.
        return result.get();
      }
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException(cause);
    } catch (TimeoutException _) {
      throw new IllegalStateException(
          "The UI thread did not answer within "
              + CALL_TIMEOUT.toSeconds()
              + " s; the call was dropped");
    } catch (InterruptedException e) {
      state.compareAndSet(WAITING, ABANDONED);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the UI thread", e);
    }
  }
}
