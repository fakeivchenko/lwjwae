package dev.ivchenko.lwjwae.exception;

import java.io.Serial;

/**
 * A statement broke a constraint of the database: {@code UNIQUE}, {@code PRIMARY KEY}, {@code NOT
 * NULL}, {@code CHECK}, or {@code FOREIGN KEY}. {@link #getCode()} tells which.
 */
public class ConstraintViolatedException extends StoreFailedException {
  @Serial private static final long serialVersionUID = 1L;

  public ConstraintViolatedException(String message, int code) {
    super(message, code);
  }
}
