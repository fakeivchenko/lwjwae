package dev.ivchenko.lwjwae.exception;

import java.io.Serial;

/**
 * Thrown when the system refuses a global shortcut, mostly because another application, or this
 * one, holds it already.
 */
public class ShortcutUnavailableException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public ShortcutUnavailableException(String message) {
    super(message);
  }
}
