package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.event.LoadEvent;
import dev.ivchenko.lwjwae.event.LoadState;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;

/** Waits for page loads, so that tests read state only after the engine has settled. */
@UtilityClass
public class Loads {
  /**
   * Returns a future that completes on {@link LoadState#FINISHED} and fails on {@link
   * LoadState#FAILED}.
   */
  public CompletableFuture<LoadEvent> expectFinished(ApplicationBackend backend) {
    CompletableFuture<LoadEvent> loaded = new CompletableFuture<>();
    backend.onLoad(
        event -> {
          if (event.state() == LoadState.FAILED) {
            loaded.completeExceptionally(
                new IllegalStateException("Load failed: " + event.message()));
          } else if (event.state() == LoadState.FINISHED) {
            loaded.complete(event);
          }
        });
    return loaded;
  }

  /** Returns a future that completes on the first {@link LoadState#FAILED}. */
  public CompletableFuture<LoadEvent> expectFailed(ApplicationBackend backend) {
    CompletableFuture<LoadEvent> failed = new CompletableFuture<>();
    backend.onLoad(
        event -> {
          if (event.state() == LoadState.FAILED) {
            failed.complete(event);
          }
        });
    return failed;
  }

  /** Evaluates {@code script} and waits for the answer. */
  public String eval(ApplicationBackend backend, String script) throws Exception {
    return backend.eval(script).get(20, TimeUnit.SECONDS);
  }

  /** Polls a page expression until it's no longer {@code undefined}. */
  public String awaitValue(ApplicationBackend backend, String expression) throws Exception {
    for (int attempt = 0; attempt < 100; attempt++) {
      String value = eval(backend, "String(%s)".formatted(expression));
      if (!"undefined".equals(value)) {
        return value;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Timed out waiting for " + expression);
  }
}
