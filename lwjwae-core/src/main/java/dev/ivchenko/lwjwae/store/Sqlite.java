package dev.ivchenko.lwjwae.store;

import dev.ivchenko.lwjwae.foreign.Layouts;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
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

  /** {@code SQLITE_DENY}: the authorizer refuses the statement, which then fails to compile. */
  private final int DENY = 1;

  /** {@code SQLITE_PRAGMA}, {@code SQLITE_ATTACH}, {@code SQLITE_DETACH}: authorizer actions. */
  private final int PRAGMA = 19;

  private final int ATTACH = 24;
  private final int DETACH = 25;

  /**
   * The {@code PRAGMA}s that a page may not run: where SQLite writes its files, whether it trusts
   * the schema, and how the file is journaled and locked, which the store decides.
   */
  private final Set<String> PRAGMAS_REFUSED_TO_PAGES =
      Set.of(
          "data_store_directory",
          "journal_mode",
          "locking_mode",
          "temp_store_directory",
          "trusted_schema",
          "writable_schema");

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
  private final MethodHandle SET_AUTHORIZER =
      NativeLibraries.downcall(
          SQLITE,
          "sqlite3_set_authorizer",
          FunctionDescriptor.of(
              Layouts.C_INT, Layouts.C_POINTER, Layouts.C_POINTER, Layouts.C_POINTER));

  /** The C side of {@link #authorizeForPage}. */
  private final MemorySegment PAGE_AUTHORIZER =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Sqlite.class,
          "authorizeForPage",
          MethodType.methodType(
              int.class,
              MemorySegment.class,
              int.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class),
          FunctionDescriptor.of(
              Layouts.C_INT,
              Layouts.C_POINTER,
              Layouts.C_INT,
              Layouts.C_POINTER,
              Layouts.C_POINTER,
              Layouts.C_POINTER,
              Layouts.C_POINTER));

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

  /**
   * Calls {@code sqlite3_set_authorizer} with {@link #authorizeForPage} while {@code pages} is
   * true, and with none otherwise. Statements compiled meanwhile are checked.
   */
  @SneakyThrows
  int authorizeForPages(MemorySegment database, boolean pages) {
    MemorySegment authorizer = pages ? PAGE_AUTHORIZER : MemorySegment.NULL;
    return (int) SET_AUTHORIZER.invokeExact(database, authorizer, MemorySegment.NULL);
  }

  /**
   * Decides whether a statement of a page may do {@code action}, as SQLite compiles it. A page
   * keeps to the file of the store: {@code ATTACH}, which {@code VACUUM INTO} runs as well, would
   * open or create any file that the process may write, and {@link #PRAGMAS_REFUSED_TO_PAGES}
   * change where and how SQLite writes.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   *
   * @param first The name of the pragma for {@code SQLITE_PRAGMA}.
   */
  @SuppressWarnings("unused")
  private int authorizeForPage(
      MemorySegment userData,
      int action,
      MemorySegment first,
      MemorySegment second,
      MemorySegment database,
      MemorySegment trigger) {
    try {
      return switch (action) {
        case ATTACH, DETACH -> DENY;
        case PRAGMA -> {
          String pragma = NativeLibraries.string(first);
          yield pragma != null && PRAGMAS_REFUSED_TO_PAGES.contains(pragma.toLowerCase(Locale.ROOT))
              ? DENY
              : OK;
        }
        default -> OK;
      };
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      return DENY;
    }
  }
}
