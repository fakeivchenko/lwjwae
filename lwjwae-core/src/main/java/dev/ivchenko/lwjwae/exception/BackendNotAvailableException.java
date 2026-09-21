package dev.ivchenko.lwjwae.exception;

/**
 * Thrown when no backend on the classpath supports the machine that the application runs on.
 *
 * <p>Either no backend artifact was added as a runtime dependency, or the present backends rejected
 * this platform. On Linux, the second case usually means that GTK or WebKitGTK isn't installed. The
 * message lists what was found, so you can tell the two cases apart without a debugger.
 */
public class BackendNotAvailableException extends RuntimeException {
  public BackendNotAvailableException(String message) {
    super(message);
  }
}
