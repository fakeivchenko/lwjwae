package dev.ivchenko.lwjwae.store;

import dev.ivchenko.lwjwae.exception.ConstraintViolatedException;
import dev.ivchenko.lwjwae.exception.StoreFailedException;
import dev.ivchenko.lwjwae.foreign.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One connection to a database file of SQLite: statements with parameters, and rows of typed
 * values.
 *
 * <p>The connection is opened in the serialized mode of SQLite, and the store calls it under a lock
 * of its own anyway, so it may be used from any thread. A parameter is a {@link String}, a whole
 * {@link Number}, another {@link Number}, a {@link Boolean} as 1 or 0, a {@code byte[]}, or {@code
 * null}.
 */
final class SqliteDatabase implements AutoCloseable {
  private static final int BUSY_TIMEOUT_MILLIS = 5000;

  private MemorySegment database;

  /**
   * Opens {@code path}, creating it if it isn't there, or a database in memory for {@code
   * :memory:}.
   *
   * @throws StoreFailedException If SQLite can't open it.
   */
  SqliteDatabase(String path) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Layouts.C_POINTER);
      int result = Sqlite.open(arena.allocateFrom(path), out);
      MemorySegment opened = out.get(Layouts.C_POINTER, 0);
      if (result != Sqlite.OK) {
        String message = opened.equals(MemorySegment.NULL) ? "" : Sqlite.errorMessage(opened);
        if (!opened.equals(MemorySegment.NULL)) {
          Sqlite.close(opened);
        }
        throw new StoreFailedException("Can't open " + path + ": " + message, result);
      }
      this.database = opened;
    }
    Sqlite.busyTimeout(this.database, BUSY_TIMEOUT_MILLIS);
  }

  /**
   * Runs one statement with {@code parameters}, a {@link List} by position or a {@link Map} by
   * name, and returns its rows, each a map from the name of a column to its value.
   *
   * @throws IllegalArgumentException If {@code sql} has more than one statement, or a parameter
   *     that {@code parameters} doesn't give.
   * @throws ConstraintViolatedException If a constraint refused a row.
   * @throws StoreFailedException If SQLite refused the statement.
   */
  List<Map<String, Object>> query(String sql, Object parameters) {
    try (Arena arena = Arena.ofConfined()) {
      SqliteStatement prepared = this.prepare(arena, arena.allocateFrom(sql), sql);
      if (prepared.isEmpty()) {
        throw new IllegalArgumentException("No statement in: " + sql);
      }
      MemorySegment statement = prepared.handle();
      try {
        this.requireOnlyStatement(arena, prepared.tail(), sql);
        this.bind(arena, statement, parameters, sql);
        List<Map<String, Object>> rows = new ArrayList<>();
        String[] names = null;
        while (true) {
          int result = Sqlite.step(statement);
          if (result == Sqlite.DONE) {
            return rows;
          }
          if (result != Sqlite.ROW) {
            this.check(result, sql);
          }
          if (names == null) {
            names = new String[Sqlite.columnCount(statement)];
            for (int column = 0; column < names.length; column++) {
              names[column] = Sqlite.columnName(statement, column);
            }
          }
          Map<String, Object> row = new LinkedHashMap<>();
          for (int column = 0; column < names.length; column++) {
            row.put(names[column], Sqlite.columnValue(statement, column));
          }
          rows.add(row);
        }
      } finally {
        Sqlite.finalizeStatement(statement);
      }
    }
  }

  /**
   * Runs one statement as {@link #query} does, and returns how many rows it changed.
   *
   * @throws ConstraintViolatedException If a constraint refused a row.
   * @throws StoreFailedException If SQLite refused the statement.
   */
  int execute(String sql, Object parameters) {
    this.query(sql, parameters);
    return Sqlite.changes(this.database());
  }

  /**
   * Runs every statement of {@code sql}, which take no parameters, in order, until one fails.
   *
   * @throws ConstraintViolatedException If a constraint refused a row.
   * @throws StoreFailedException If SQLite refused a statement.
   */
  void executeScript(String sql) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment next = arena.allocateFrom(sql);
      while (next != null) {
        SqliteStatement prepared = this.prepare(arena, next, sql);
        next = prepared.tail();
        if (prepared.isEmpty()) {
          return;
        }
        MemorySegment statement = prepared.handle();
        try {
          int result;
          do {
            result = Sqlite.step(statement);
          } while (result == Sqlite.ROW);
          if (result != Sqlite.DONE) {
            this.check(result, sql);
          }
        } finally {
          Sqlite.finalizeStatement(statement);
        }
      }
    }
  }

  /** The {@code ROWID} of the last row that an {@code INSERT} added on this connection. */
  long lastInsertRowId() {
    return Sqlite.lastInsertRowId(this.database());
  }

  @Override
  public void close() {
    MemorySegment current = this.database;
    this.database = null;
    if (current != null) {
      Sqlite.close(current);
    }
  }

  /** Compiles the first statement of {@code text}. */
  private SqliteStatement prepare(Arena arena, MemorySegment text, String sql) {
    MemorySegment statementOut = arena.allocate(Layouts.C_POINTER);
    MemorySegment tailOut = arena.allocate(Layouts.C_POINTER);
    this.check(Sqlite.prepare(this.database(), text, statementOut, tailOut), sql);
    MemorySegment tail = tailOut.get(Layouts.C_POINTER, 0);
    boolean ended =
        tail.equals(MemorySegment.NULL) || tail.reinterpret(1).get(Layouts.C_CHAR, 0) == 0;
    return new SqliteStatement(statementOut.get(Layouts.C_POINTER, 0), ended ? null : tail);
  }

  /** Refuses a second statement after the first one, which would never run. */
  private void requireOnlyStatement(Arena arena, MemorySegment tail, String sql) {
    if (tail == null) {
      return;
    }
    SqliteStatement extra = this.prepare(arena, tail, sql);
    if (!extra.isEmpty()) {
      Sqlite.finalizeStatement(extra.handle());
      throw new IllegalArgumentException(
          "One statement at a time; run a script with executeScript: " + sql);
    }
  }

  private void bind(Arena arena, MemorySegment statement, Object parameters, String sql) {
    int count = Sqlite.bindParameterCount(statement);
    for (int index = 1; index <= count; index++) {
      Object value =
          switch (parameters) {
            case List<?> list -> {
              if (index > list.size()) {
                throw new IllegalArgumentException(
                    "No value for parameter " + index + " of " + sql);
              }
              yield list.get(index - 1);
            }
            case Map<?, ?> map -> {
              String name = Sqlite.bindParameterName(statement, index);
              String key = name == null ? null : name.substring(1);
              if (key == null || !map.containsKey(key)) {
                throw new IllegalArgumentException(
                    "No value for parameter " + (name == null ? index : name) + " of " + sql);
              }
              yield map.get(key);
            }
            default ->
                throw new IllegalArgumentException("Parameters are a List or a Map: " + parameters);
          };
      this.check(SqliteDatabase.bindValue(arena, statement, index, value), sql);
    }
  }

  private static int bindValue(Arena arena, MemorySegment statement, int index, Object value) {
    return switch (value) {
      case null -> Sqlite.bindNull(statement, index);
      case String text -> {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        yield Sqlite.bindText(statement, index, SqliteDatabase.copy(arena, bytes), bytes.length);
      }
      case byte[] bytes ->
          Sqlite.bindBlob(statement, index, SqliteDatabase.copy(arena, bytes), bytes.length);
      case Boolean flag -> Sqlite.bindLong(statement, index, flag ? 1 : 0);
      case Long number -> Sqlite.bindLong(statement, index, number);
      case Integer number -> Sqlite.bindLong(statement, index, number);
      case Short number -> Sqlite.bindLong(statement, index, number);
      case Byte number -> Sqlite.bindLong(statement, index, number);
      case Number number -> Sqlite.bindDouble(statement, index, number.doubleValue());
      default ->
          throw new IllegalArgumentException("Not a parameter of SQLite: " + value.getClass());
    };
  }

  private static MemorySegment copy(Arena arena, byte[] bytes) {
    MemorySegment copy = arena.allocate(Math.max(bytes.length, 1));
    MemorySegment.copy(bytes, 0, copy, Layouts.C_CHAR, 0, bytes.length);
    return copy;
  }

  private void check(int result, String sql) {
    if (result == Sqlite.OK) {
      return;
    }
    MemorySegment current = this.database();
    int code = Sqlite.extendedErrorCode(current);
    String message = Sqlite.errorMessage(current);
    if ((code & 0xFF) == Sqlite.CONSTRAINT) {
      throw new ConstraintViolatedException(message, code);
    }
    throw new StoreFailedException(message + " in " + sql, code);
  }

  private MemorySegment database() {
    MemorySegment current = this.database;
    if (current == null) {
      throw new IllegalStateException("The store is closed");
    }
    return current;
  }
}
