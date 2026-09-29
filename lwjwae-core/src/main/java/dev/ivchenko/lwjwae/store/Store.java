package dev.ivchenko.lwjwae.store;

import dev.ivchenko.lwjwae.exception.ConstraintViolatedException;
import dev.ivchenko.lwjwae.exception.StoreFailedException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A database of SQLite in one file, with all of the SQL of SQLite: tables, indexes, views,
 * triggers, joins, window functions, common table expressions, JSON, full-text search where the
 * library has it, and {@code PRAGMA}s.
 *
 * <pre>{@code
 * Store store = application.store();
 * store.executeScript("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY, title TEXT)");
 * store.execute("INSERT INTO notes (title) VALUES (?)", "Groceries");
 * List<Map<String, Object>> found =
 *     store.query("SELECT * FROM notes WHERE title LIKE :prefix", Map.of("prefix", "Gro%"));
 * }</pre>
 *
 * <p>The store is the SQLite of the system, so the application carries no database of its own. It
 * opens the file in write-ahead mode, where a write is atomic and survives a crash, and readers
 * don't wait for it.
 *
 * <p>A parameter is {@code ?}, {@code ?NNN} by position, or {@code :name}, {@code @name}, or {@code
 * $name} by name, and its value a {@link String}, a {@link Number}, a {@link Boolean} as 1 or 0, a
 * {@code byte[]}, or {@code null}. A row is a map from the name of a column to its value, in the
 * order of the columns: a {@link Long}, a {@link Double}, a {@link String}, a {@code byte[]}, or
 * {@code null}, by the type of the value in SQLite. Two columns of one name keep the last value;
 * {@code AS} tells them apart.
 *
 * <p>Every method is safe to call from any thread; one runs at a time, and a transaction keeps the
 * others waiting until it ends.
 */
public final class Store implements AutoCloseable {
  private final SqliteDatabase database;
  private final ReentrantLock lock = new ReentrantLock();

  /** How many transactions of the thread that holds the lock are open, one in another. */
  private int transactionDepth;

  private Store(SqliteDatabase database) {
    this.database = database;
    this.database.execute("PRAGMA journal_mode=WAL", List.of());
    this.database.execute("PRAGMA foreign_keys=ON", List.of());
  }

  /**
   * Opens the store in {@code file}, creating it and its directory if they aren't there.
   *
   * @throws UncheckedIOException If the directory can't be created.
   * @throws StoreFailedException If SQLite can't open the file.
   */
  public static Store open(Path file) {
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new Store(new SqliteDatabase(file.toAbsolutePath().toString()));
  }

  /** A store in memory, gone when it closes: for tests, and for data that no run keeps. */
  public static Store inMemory() {
    return new Store(new SqliteDatabase(":memory:"));
  }

  /**
   * Runs one statement with parameters by position, and returns its rows.
   *
   * @throws IllegalArgumentException If {@code sql} has more than one statement, or more parameters
   *     than {@code parameters} gives.
   * @throws ConstraintViolatedException If a constraint refused a row.
   * @throws StoreFailedException If SQLite refused the statement.
   */
  public List<Map<String, Object>> query(String sql, Object... parameters) {
    return this.locked(() -> this.database.query(sql, Store.positional(parameters)));
  }

  /**
   * Runs one statement with parameters by name, without their prefix, and returns its rows.
   *
   * @throws IllegalArgumentException If {@code sql} has more than one statement, or a parameter
   *     that {@code parameters} doesn't name.
   * @throws ConstraintViolatedException If a constraint refused a row.
   * @throws StoreFailedException If SQLite refused the statement.
   */
  public List<Map<String, Object>> query(String sql, Map<String, ?> parameters) {
    Objects.requireNonNull(parameters, "parameters");
    return this.locked(() -> this.database.query(sql, parameters));
  }

  /**
   * Runs one statement with parameters by position, and returns how many rows it inserted, updated,
   * or deleted.
   *
   * @throws IllegalArgumentException If {@code sql} has more than one statement, or more parameters
   *     than {@code parameters} gives.
   * @throws ConstraintViolatedException If a constraint refused a row.
   * @throws StoreFailedException If SQLite refused the statement.
   */
  public int execute(String sql, Object... parameters) {
    return this.locked(() -> this.database.execute(sql, Store.positional(parameters)));
  }

  /**
   * Runs one statement with parameters by name, and returns how many rows it changed.
   *
   * @throws IllegalArgumentException If {@code sql} has more than one statement, or a parameter
   *     that {@code parameters} doesn't name.
   * @throws ConstraintViolatedException If a constraint refused a row.
   * @throws StoreFailedException If SQLite refused the statement.
   */
  public int execute(String sql, Map<String, ?> parameters) {
    Objects.requireNonNull(parameters, "parameters");
    return this.locked(() -> this.database.execute(sql, parameters));
  }

  /**
   * Runs every statement of {@code sql} in order, such as a schema or a migration, until one fails.
   * The statements take no parameters, and their rows are dropped.
   *
   * @throws ConstraintViolatedException If a constraint refused a row.
   * @throws StoreFailedException If SQLite refused a statement.
   */
  public void executeScript(String sql) {
    this.locked(
        () -> {
          this.database.executeScript(sql);
          return null;
        });
  }

  /**
   * The {@code ROWID} of the row that the last {@code INSERT} added. Another thread may insert in
   * between, so read it in the transaction of the insert.
   */
  public long lastInsertRowId() {
    return this.locked(this.database::lastInsertRowId);
  }

  /**
   * Runs {@code work} in a transaction: every statement of it happens, or none, if it throws. A
   * transaction in another is a savepoint of it.
   */
  public void transaction(Consumer<Store> work) {
    this.inTransaction(
        store -> {
          work.accept(store);
          return null;
        });
  }

  /** Runs {@code work} in a transaction as {@link #transaction} does, and returns its result. */
  public <T> T inTransaction(Function<Store, T> work) {
    return this.locked(
        () -> {
          boolean outermost = this.transactionDepth == 0;
          String savepoint = "lwjwae_" + this.transactionDepth;
          this.database.execute(
              outermost ? "BEGIN IMMEDIATE" : "SAVEPOINT " + savepoint, List.of());
          this.transactionDepth++;
          try {
            T result = work.apply(this);
            this.database.execute(outermost ? "COMMIT" : "RELEASE " + savepoint, List.of());
            return result;
          } catch (RuntimeException | Error e) {
            if (outermost) {
              this.database.execute("ROLLBACK", List.of());
            } else {
              this.database.execute("ROLLBACK TO " + savepoint, List.of());
              this.database.execute("RELEASE " + savepoint, List.of());
            }
            throw e;
          } finally {
            this.transactionDepth--;
          }
        });
  }

  /** Closes the file. The store can't be used afterwards. Idempotent. */
  @Override
  public void close() {
    this.locked(
        () -> {
          this.database.close();
          return null;
        });
  }

  private static List<Object> positional(Object[] parameters) {
    return parameters == null ? List.of() : Arrays.asList(parameters);
  }

  /** Runs {@code action} while no other call of the store runs. */
  private <T> T locked(Supplier<T> action) {
    this.lock.lock();
    try {
      return action.get();
    } finally {
      this.lock.unlock();
    }
  }
}
