package dev.ivchenko.lwjwae.dialog;

import dev.ivchenko.lwjwae.ui.UiDispatcher;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The link between a native dialog and the future that the application holds: the backend completes
 * it with the answer of the user, and the application cancels the future to close the dialog.
 *
 * <p>A dialog may run a loop of its own on the UI thread until the user answers, as the modal
 * dialogs of Windows do, so the backend registers how to close it with {@link #onCancel} before it
 * shows it. A cancellation runs that on the UI thread, where a dialog in its loop still gets it.
 *
 * @param <T> The answer of the dialog.
 */
public final class DialogCompletion<T> {
  private static final Runnable CLOSED = () -> {};

  private final CompletableFuture<T> future = new CompletableFuture<>();
  private final UiDispatcher dispatcher;
  private final AtomicReference<Runnable> closer = new AtomicReference<>();

  /** A completion whose closer runs on the UI thread of {@code dispatcher}. */
  public DialogCompletion(UiDispatcher dispatcher) {
    this.dispatcher = dispatcher;
    this.future.whenComplete(
        (_, _) -> {
          if (this.future.isCancelled()) {
            this.close();
          }
        });
  }

  /** The future that the application holds. */
  public CompletableFuture<T> future() {
    return this.future;
  }

  /**
   * Registers how to close the dialog, once it's shown: it runs on the UI thread if the future is
   * cancelled, at once if it already was.
   */
  public void onCancel(Runnable closer) {
    if (!this.closer.compareAndSet(null, closer)) {
      // Cancelled before the dialog was shown: close it now.
      this.dispatcher.post(closer);
    }
  }

  /** Hands the answer of the user to the application. */
  public void complete(T answer) {
    this.future.complete(answer);
  }

  /** Reports that the dialog failed. */
  public void fail(Throwable failure) {
    this.future.completeExceptionally(failure);
  }

  /** Whether the application no longer waits: answered, failed, or cancelled. */
  public boolean isDone() {
    return this.future.isDone();
  }

  private void close() {
    Runnable registered = this.closer.getAndSet(CLOSED);
    if (registered != null && registered != CLOSED) {
      this.dispatcher.post(registered);
    }
  }
}
