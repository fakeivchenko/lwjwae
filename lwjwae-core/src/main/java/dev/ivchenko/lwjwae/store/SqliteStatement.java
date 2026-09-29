package dev.ivchenko.lwjwae.store;

import java.lang.foreign.MemorySegment;

/**
 * A statement that SQLite compiled from the start of a text, and where the rest of the text starts.
 *
 * @param handle The {@code sqlite3_stmt}, or {@code NULL} if the text held only white space and
 *     comments.
 * @param tail The text after the statement, or {@code null} at the end.
 */
record SqliteStatement(MemorySegment handle, MemorySegment tail) {
  /** Whether the text held a statement at all. */
  boolean isEmpty() {
    return this.handle.equals(MemorySegment.NULL);
  }
}
