package dev.ivchenko.lwjwae.exception;

import java.io.Serial;
import lombok.Getter;

/** SQLite refused or failed a statement of the store. */
@Getter
public class StoreFailedException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  /**
   * The extended result code of SQLite, such as {@code 1} for an error of SQL or {@code 2067} for a
   * unique index.
   */
  private final int code;

  public StoreFailedException(String message, int code) {
    super(message + " (SQLite " + code + ")");
    this.code = code;
  }
}
