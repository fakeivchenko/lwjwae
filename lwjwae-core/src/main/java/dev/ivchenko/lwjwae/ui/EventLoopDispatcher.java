package dev.ivchenko.lwjwae.ui;

import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link UiDispatcher} that owns its UI thread. It starts the thread, initializes the toolkit on
 * it, and runs the event loop of the toolkit there for the rest of the process lifetime.
 *
 * <p>A subclass supplies the toolkit specifics: {@link #initialize()}, {@link #runEventLoop()}, and
 * {@link #wakeUp()}.
 */
public abstract class EventLoopDispatcher extends UiDispatcher {
  private final String threadName;
  private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
  private final CountDownLatch initialized = new CountDownLatch(1);

  /** Whether a drain is on its way: one wake-up serves every task that is queued before it runs. */
  private final AtomicBoolean scheduled = new AtomicBoolean();

  private volatile Thread thread;
  private volatile Throwable initFailure;

  /**
   * Creates a dispatcher whose thread is named {@code threadName}. Nothing starts until {@link
   * #start()}.
   */
  protected EventLoopDispatcher(String threadName) {
    this.threadName = threadName;
  }

  /**
   * Starts the UI thread and blocks until the toolkit is initialized. This method is idempotent.
   * Every later call only verifies that initialization succeeded.
   *
   * @throws IllegalStateException If the toolkit couldn't be initialized.
   */
  public final void start() {
    this.startThread();
    try {
      this.initialized.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for " + this.threadName, e);
    }
    this.checkInitialized();
  }

  @Override
  public final boolean isDispatchThread() {
    return Thread.currentThread() == this.thread;
  }

  @Override
  public final void post(Runnable task) {
    this.checkInitialized();
    this.tasks.add(task);
    if (this.scheduled.compareAndSet(false, true)) {
      this.wakeUp();
    }
  }

  /**
   * Runs every queued task. A subclass calls this method from the callback that {@link #wakeUp()}
   * schedules on the event loop. Task failures are reported, never propagated, so that nothing
   * unwinds into native code.
   */
  protected final void drainTasks() {
    // Cleared before the queue is read: a task that comes while it drains schedules the next drain.
    this.scheduled.set(false);
    Runnable task;
    while ((task = this.tasks.poll()) != null) {
      // A task may run a loop of its own, a modal dialog: what waits behind it needs a wake-up that
      // that loop delivers, or it would wait for the dialog to close.
      if (!this.tasks.isEmpty() && this.scheduled.compareAndSet(false, true)) {
        this.wakeUp();
      }
      try {
        task.run();
      } catch (Throwable t) {
        ThrowableUtil.report(t);
      }
    }
  }

  /**
   * Called by the UI thread once, before the event loop starts. Subclasses implement this method to
   * initialize the toolkit.
   *
   * @throws RuntimeException If the toolkit is unavailable. The failure is rethrown to the caller
   *     of {@link #start()} or {@link #post}.
   */
  protected abstract void initialize();

  /**
   * Called by the UI thread after {@link #initialize()}. Subclasses implement this method to run
   * the event loop.
   */
  protected abstract void runEventLoop();

  /**
   * Asks the event loop to call {@link #drainTasks()} soon. This method is called from arbitrary
   * threads, so the underlying toolkit primitive must be thread-safe.
   */
  protected abstract void wakeUp();

  private synchronized void startThread() {
    if (this.thread != null) {
      return;
    }

    Thread worker = new Thread(this::loop, this.threadName);
    worker.setDaemon(true);
    this.thread = worker;
    worker.start();
  }

  private void loop() {
    try {
      this.initialize();
    } catch (Throwable t) {
      this.initFailure = t;
    } finally {
      this.initialized.countDown();
    }
    if (this.initFailure == null) {
      // A wake-up from before the toolkit could take one may be lost, and a lost one would keep
      // every later post from waking the thread: one wake-up now serves whatever is queued.
      this.scheduled.set(true);
      this.wakeUp();
      this.runEventLoop();
    }
  }

  private void checkInitialized() {
    Throwable failure = this.initFailure;
    if (failure != null) {
      throw new IllegalStateException("UI toolkit is not available", failure);
    }
  }
}
