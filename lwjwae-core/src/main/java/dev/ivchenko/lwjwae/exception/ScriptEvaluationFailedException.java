package dev.ivchenko.lwjwae.exception;

import java.io.Serial;

/**
 * Thrown when a script passed to {@link dev.ivchenko.lwjwae.Window#eval(String)} throws, or when
 * the engine refuses to run it.
 *
 * <p>The exception is declared in the core module instead of in a backend, because every engine can
 * fail this way and callers shouldn't catch a different type per platform. It arrives through the
 * returned future, never from the {@code eval} call itself, because evaluation is asynchronous.
 */
public class ScriptEvaluationFailedException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public ScriptEvaluationFailedException(String message) {
    super(message);
  }
}
