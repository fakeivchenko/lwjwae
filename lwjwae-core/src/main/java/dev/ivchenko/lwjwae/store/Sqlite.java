package dev.ivchenko.lwjwae.store;

import dev.ivchenko.lwjwae.foreign.Layouts;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the SQLite of the system, which every platform has: {@code winsqlite3.dll} in the
 * system directory of Windows 10 and later, {@code libsqlite3.dylib} on macOS, and {@code
 * libsqlite3.so.0} on Linux, where WebKitGTK depends on it.
 *
 * <p>The library opens on the first use of the class, not before: an application without a store
 * never loads it.
 */
@UtilityClass
class Sqlite {
  /** {@code SQLITE_OK}, {@code SQLITE_ROW}, {@code SQLITE_DONE}. */
  final int OK = 0;

  final int ROW = 100;
  final int DONE = 101;

  /** {@code SQLITE_CONSTRAINT}: a constraint refused a row. */
  final int CONSTRAINT = 19;

  /** {@code SQLITE_INTEGER}, {@code SQLITE_FLOAT}, {@code SQLITE_TEXT}, {@code SQLITE_BLOB}. */
  final int INTEGER = 1;

  final int FLOAT = 2;
  final int TEXT = 3;
  final int BLOB = 4;

  /** {@code SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX}. */
  final int OPEN_FLAGS = 0x2 | 0x4 | 0x10000;

  /** {@code SQLITE_TRANSIENT}: SQLite copies a bound value before the call returns. */
  private final MemorySegment TRANSIENT = MemorySegment.ofAddress(-1L);

  private final SymbolLookup SQLITE =
      NativeLibraries.load(
          "libsqlite3.so.0",
          "libsqlite3.so",
          "/usr/lib/libsqlite3.dylib",
          "libsqlite3.dylib",
          "winsqlite3",
          "sqlite3");

  private final FunctionDescriptor INT_POINTER =
      FunctionDescriptor.of(Layouts.C_INT, Layouts.C_POINTER);
  private final FunctionDescriptor INT_POINTER_INT =
      FunctionDescriptor.of(Layouts.C_INT, Layouts.C_POINTER, Layouts.C_INT);
  private final FunctionDescriptor POINTER_POINTER =
      FunctionDescriptor.of(Layouts.C_POINTER, Layouts.C_POINTER);
  private final FunctionDescriptor POINTER_POINTER_INT =
      FunctionDescriptor.of(Layouts.C_POINTER, Layouts.C_POINTER, Layouts.C_INT);

  private final MethodHandle OPEN_V2 =
      NativeLibraries.downcall(
          SQLITE,
          "sqlite3_open_v2",
          FunctionDescriptor.of(
              Layouts.C_INT,
              Layouts.C_POINTER,
              Layouts.C_POINTER,
              Layouts.C_INT,
              Layouts.C_POINTER));
  private final MethodHandle CLOSE_V2 =
      NativeLibraries.downcall(SQLITE, "sqlite3_close_v2", INT_POINTER);
  private final MethodHandle PREPARE_V2 =
      NativeLibraries.downcall(
          SQLITE,
          "sqlite3_prepare_v2",
          FunctionDescriptor.of(
              Layouts.C_INT,
              Layouts.C_POINTER,
              Layouts.C_POINTER,
              Layouts.C_INT,
              Layouts.C_POINTER,
              Layouts.C_POINTER));
  private final MethodHandle BIND_TEXT =
      NativeLibraries.downcall(
          SQLITE,
          "sqlite3_bind_text",
          FunctionDescriptor.of(
              Layouts.C_INT,
              Layouts.C_POINTER,
              Layouts.C_INT,
              Layouts.C_POINTER,
              Layouts.C_INT,
              Layouts.C_POINTER));
  private final MethodHandle BIND_BLOB =
      NativeLibraries.downcall(
          SQLITE,
          "sqlite3_bind_blob",
          FunctionDescriptor.of(
              Layouts.C_INT,
              Layouts.C_POINTER,
              Layouts.C_INT,
              Layouts.C_POINTER,
              Layouts.C_INT,
              Layouts.C_POINTER));
  private final MethodHandle BIND_PARAMETER_COUNT =
      NativeLibraries.downcall(SQLITE, "sqlite3_bind_parameter_count", INT_POINTER);
  private final MethodHandle BIND_PARAMETER_NAME =
      NativeLibraries.downcall(SQLITE, "sqlite3_bind_parameter_name", POINTER_POINTER_INT);
  private final MethodHandle BIND_INT64 =
      NativeLibraries.downcall(
          SQLITE,
          "sqlite3_bind_int64",
          FunctionDescriptor.of(
              Layouts.C_INT, Layouts.C_POINTER, Layouts.C_INT, Layouts.C_LONG_LONG));
  private final MethodHandle BIND_DOUBLE =
      NativeLibraries.downcall(
          SQLITE,
          "sqlite3_bind_double",
          FunctionDescriptor.of(Layouts.C_INT, Layouts.C_POINTER, Layouts.C_INT, Layouts.C_DOUBLE));
  private final MethodHandle BIND_NULL =
      NativeLibraries.downcall(SQLITE, "sqlite3_bind_null", INT_POINTER_INT);
  private final MethodHandle STEP = NativeLibraries.downcall(SQLITE, "sqlite3_step", INT_POINTER);
  private final MethodHandle COLUMN_COUNT =
      NativeLibraries.downcall(SQLITE, "sqlite3_column_count", INT_POINTER);
  private final MethodHandle COLUMN_TEXT =
      NativeLibraries.downcall(SQLITE, "sqlite3_column_text", POINTER_POINTER_INT);
  private final MethodHandle COLUMN_NAME =
      NativeLibraries.downcall(SQLITE, "sqlite3_column_name", POINTER_POINTER_INT);
  private final MethodHandle COLUMN_TYPE =
      NativeLibraries.downcall(SQLITE, "sqlite3_column_type", INT_POINTER_INT);
  private final MethodHandle COLUMN_INT64 =
      NativeLibraries.downcall(
          SQLITE,
          "sqlite3_column_int64",
          FunctionDescriptor.of(Layouts.C_LONG_LONG, Layouts.C_POINTER, Layouts.C_INT));
  private final MethodHandle COLUMN_DOUBLE =
      NativeLibraries.downcall(
          SQLITE,
          "sqlite3_column_double",
          FunctionDescriptor.of(Layouts.C_DOUBLE, Layouts.C_POINTER, Layouts.C_INT));
  private final MethodHandle COLUMN_BLOB =
      NativeLibraries.downcall(SQLITE, "sqlite3_column_blob", POINTER_POINTER_INT);
  private final MethodHandle COLUMN_BYTES =
      NativeLibraries.downcall(SQLITE, "sqlite3_column_bytes", INT_POINTER_INT);
  private final MethodHandle FINALIZE =
      NativeLibraries.downcall(SQLITE, "sqlite3_finalize", INT_POINTER);
  private final MethodHandle ERRMSG =
      NativeLibraries.downcall(SQLITE, "sqlite3_errmsg", POINTER_POINTER);
  private final MethodHandle EXTENDED_ERRCODE =
      NativeLibraries.downcall(SQLITE, "sqlite3_extended_errcode", INT_POINTER);
  private final MethodHandle CHANGES =
      NativeLibraries.downcall(SQLITE, "sqlite3_changes", INT_POINTER);
  private final MethodHandle LAST_INSERT_ROWID =
      NativeLibraries.downcall(
          SQLITE,
          "sqlite3_last_insert_rowid",
          FunctionDescriptor.of(Layouts.C_LONG_LONG, Layouts.C_POINTER));
  private final MethodHandle BUSY_TIMEOUT =
      NativeLibraries.downcall(SQLITE, "sqlite3_busy_timeout", INT_POINTER_INT);

  @SneakyThrows
  int open(MemorySegment filename, MemorySegment database) {
    return (int) OPEN_V2.invokeExact(filename, database, OPEN_FLAGS, MemorySegment.NULL);
  }

  @SneakyThrows
  int close(MemorySegment database) {
    return (int) CLOSE_V2.invokeExact(database);
  }

  /** Compiles the first statement of {@code sql}, and points {@code tail} at the text after it. */
  @SneakyThrows
  int prepare(
      MemorySegment database, MemorySegment sql, MemorySegment statement, MemorySegment tail) {
    return (int) PREPARE_V2.invokeExact(database, sql, -1, statement, tail);
  }

  /** Binds UTF-8 {@code text} of {@code length} bytes, which SQLite copies. */
  @SneakyThrows
  int bindText(MemorySegment statement, int index, MemorySegment text, int length) {
    return (int) BIND_TEXT.invokeExact(statement, index, text, length, TRANSIENT);
  }

  /** Binds {@code length} bytes of {@code blob}, which SQLite copies. */
  @SneakyThrows
  int bindBlob(MemorySegment statement, int index, MemorySegment blob, int length) {
    return (int) BIND_BLOB.invokeExact(statement, index, blob, length, TRANSIENT);
  }

  @SneakyThrows
  int bindParameterCount(MemorySegment statement) {
    return (int) BIND_PARAMETER_COUNT.invokeExact(statement);
  }

  /** The name of parameter {@code index} with its prefix, such as {@code :id}, or {@code null}. */
  @SneakyThrows
  String bindParameterName(MemorySegment statement, int index) {
    MemorySegment name = (MemorySegment) BIND_PARAMETER_NAME.invokeExact(statement, index);
    return name.equals(MemorySegment.NULL) ? null : NativeLibraries.string(name);
  }

  @SneakyThrows
  int bindLong(MemorySegment statement, int index, long value) {
    return (int) BIND_INT64.invokeExact(statement, index, value);
  }

  @SneakyThrows
  int bindDouble(MemorySegment statement, int index, double value) {
    return (int) BIND_DOUBLE.invokeExact(statement, index, value);
  }

  @SneakyThrows
  int bindNull(MemorySegment statement, int index) {
    return (int) BIND_NULL.invokeExact(statement, index);
  }

  @SneakyThrows
  int step(MemorySegment statement) {
    return (int) STEP.invokeExact(statement);
  }

  @SneakyThrows
  int columnCount(MemorySegment statement) {
    return (int) COLUMN_COUNT.invokeExact(statement);
  }

  @SneakyThrows
  String columnName(MemorySegment statement, int index) {
    return NativeLibraries.string((MemorySegment) COLUMN_NAME.invokeExact(statement, index));
  }

  /**
   * The value of column {@code index} of the row: a {@link Long}, a {@link Double}, a {@link
   * String}, a {@code byte[]}, or {@code null}, by the type that SQLite gives the value itself.
   */
  @SneakyThrows
  Object columnValue(MemorySegment statement, int index) {
    int type = (int) COLUMN_TYPE.invokeExact(statement, index);
    return switch (type) {
      case INTEGER -> (long) COLUMN_INT64.invokeExact(statement, index);
      case FLOAT -> (double) COLUMN_DOUBLE.invokeExact(statement, index);
      case TEXT -> {
        MemorySegment text = (MemorySegment) COLUMN_TEXT.invokeExact(statement, index);
        int length = (int) COLUMN_BYTES.invokeExact(statement, index);
        yield new String(text.reinterpret(length).toArray(Layouts.C_CHAR), StandardCharsets.UTF_8);
      }
      case BLOB -> {
        MemorySegment blob = (MemorySegment) COLUMN_BLOB.invokeExact(statement, index);
        int length = (int) COLUMN_BYTES.invokeExact(statement, index);
        yield length == 0 ? new byte[0] : blob.reinterpret(length).toArray(Layouts.C_CHAR);
      }
      default -> null;
    };
  }

  @SneakyThrows
  int finalizeStatement(MemorySegment statement) {
    return (int) FINALIZE.invokeExact(statement);
  }

  @SneakyThrows
  String errorMessage(MemorySegment database) {
    return NativeLibraries.string((MemorySegment) ERRMSG.invokeExact(database));
  }

  @SneakyThrows
  int extendedErrorCode(MemorySegment database) {
    return (int) EXTENDED_ERRCODE.invokeExact(database);
  }

  @SneakyThrows
  int changes(MemorySegment database) {
    return (int) CHANGES.invokeExact(database);
  }

  @SneakyThrows
  long lastInsertRowId(MemorySegment database) {
    return (long) LAST_INSERT_ROWID.invokeExact(database);
  }

  @SneakyThrows
  int busyTimeout(MemorySegment database, int milliseconds) {
    return (int) BUSY_TIMEOUT.invokeExact(database, milliseconds);
  }
}
