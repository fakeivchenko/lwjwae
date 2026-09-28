package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.dialog.DialogCompletion;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A dialog that a {@link FakeWindow} was asked to show: what it was asked with, the completion that
 * a test answers it through, and whether a cancellation closed it.
 */
public record PresentedDialog(
    Object parameters, DialogCompletion<?> completion, AtomicBoolean closed) {
  /** Answers the dialog as the user would. */
  @SuppressWarnings("unchecked")
  public <T> void answer(T answer) {
    ((DialogCompletion<T>) this.completion).complete(answer);
  }
}
