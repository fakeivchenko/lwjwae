package dev.ivchenko.lwjwae.exception;

/** Thrown when a classpath resource that a page asked for doesn't exist. */
public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String message) {
    super(message);
  }
}
