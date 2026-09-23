package dev.ivchenko.lwjwae.exception;

import java.io.Serial;

/** Thrown when a classpath resource that a page asked for doesn't exist. */
public class ResourceNotFoundException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
