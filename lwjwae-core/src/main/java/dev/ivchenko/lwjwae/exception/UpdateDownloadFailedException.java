package dev.ivchenko.lwjwae.exception;

import java.io.Serial;

/** The manifest, its signature, or an update couldn't be downloaded. */
public class UpdateDownloadFailedException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public UpdateDownloadFailedException(String message) {
    super(message);
  }

  public UpdateDownloadFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
