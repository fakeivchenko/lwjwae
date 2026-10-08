package dev.ivchenko.lwjwae.exception;

import java.io.Serial;

/**
 * The store of credentials of the system refused or failed a call of {@link
 * dev.ivchenko.lwjwae.secret.Secrets}: the user didn't unlock it, no store runs, or the system
 * reported an error.
 */
public class SecretStoreFailedException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public SecretStoreFailedException(String message) {
    super(message);
  }
}
