package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.ui.EventLoopDispatcher;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * An {@link EventLoopDispatcher} over a queue instead of a toolkit: the same threading, without
 * native code.
 */
public class FakeUiDispatcher extends EventLoopDispatcher {
  private final BlockingQueue<Boolean> wakeUps = new LinkedBlockingQueue<>();
  private final Throwable initFailure;

  public FakeUiDispatcher() {
    this(null);
  }

  /** Creates a dispatcher whose toolkit fails to initialize with {@code initFailure}. */
  public FakeUiDispatcher(Throwable initFailure) {
    super("fake-ui");
    this.initFailure = initFailure;
  }

  @Override
  protected void initialize() {
    if (this.initFailure instanceof RuntimeException runtime) {
      throw runtime;
    }
    if (this.initFailure instanceof Error error) {
      throw error;
    }
  }

  /**
   * Suppressed warnings: {@code InfiniteLoopStatement}: an event loop runs until the process ends,
   * by design. The only way out is an interrupt, handled below.
   */
  @SuppressWarnings("InfiniteLoopStatement")
  @Override
  protected void runEventLoop() {
    try {
      while (true) {
        this.wakeUps.take();
        this.drainTasks();
      }
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  protected void wakeUp() {
    this.wakeUps.add(Boolean.TRUE);
  }
}
