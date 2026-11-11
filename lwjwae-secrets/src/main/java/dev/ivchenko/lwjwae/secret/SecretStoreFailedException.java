package dev.ivchenko.lwjwae.secret;

import java.io.Serial;

/**
 * The store of credentials of the system refused or failed a call of {@link Secrets}: the user
 * didn't unlock it, no store runs, or the system reported an error.
 */
public class SecretStoreFailedException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  /** The failure, as the store described it. */
  public SecretStoreFailedException(String message) {
    super(message);
  }
}
