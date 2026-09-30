package dev.ivchenko.lwjwae.exception;

import java.io.Serial;

/**
 * An update failed its checks: the signature of the manifest isn't the one of the key of the
 * application, the manifest isn't one, or a download doesn't have the size or the SHA-256 that the
 * manifest gives.
 */
public class UpdateRejectedException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public UpdateRejectedException(String message) {
    super(message);
  }

  public UpdateRejectedException(String message, Throwable cause) {
    super(message, cause);
  }
}
