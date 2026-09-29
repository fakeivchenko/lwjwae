package dev.ivchenko.lwjwae.store;

import dev.ivchenko.lwjwae.exception.ConstraintViolatedException;
import dev.ivchenko.lwjwae.exception.StoreFailedException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreTest {
  private static final String SCHEMA =
      """
      CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL UNIQUE, year INTEGER,
          price REAL, cover BLOB);
      -- A comment between statements.
      CREATE INDEX books_year ON books (year);
      """;

  @Test
  void queriesReturnTypedRowsInTheOrderOfTheColumns() {
    try (Store store = Store.inMemory()) {
      store.executeScript(SCHEMA);
      Assertions.assertEquals(
          1,
          store.execute(
              "INSERT INTO books (title, year, price, cover) VALUES (?, ?, ?, ?)",
              "Dune",
              1965,
              9.5,
              new byte[] {1, 2, 3}));
      Assertions.assertEquals(1L, store.lastInsertRowId());
      store.execute(
          "INSERT INTO books (title, year) VALUES (:title, @year)",
          Map.of("title", "Emma", "year", 1815L));

      List<Map<String, Object>> rows = store.query("SELECT * FROM books ORDER BY year");
      Assertions.assertEquals(
          List.of("id", "title", "year", "price", "cover"), List.copyOf(rows.get(0).keySet()));
      Assertions.assertEquals("Emma", rows.get(0).get("title"));
      Assertions.assertNull(rows.get(0).get("price"));
      Map<String, Object> dune = rows.get(1);
      Assertions.assertEquals(1L, dune.get("id"));
      Assertions.assertEquals(1965L, dune.get("year"));
      Assertions.assertEquals(9.5, dune.get("price"));
      Assertions.assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) dune.get("cover"));
    }
  }

  @Test
  void theSqlOfSqliteWorksAsIs() {
    try (Store store = Store.inMemory()) {
      store.executeScript(SCHEMA);
      for (Object[] book :
          List.of(
              new Object[] {"Dune", 1965},
              new Object[] {"Emma", 1815},
              new Object[] {"Ubik", 1969})) {
        store.execute("INSERT INTO books (title, year) VALUES (?, ?)", book);
      }
      Assertions.assertEquals(
          List.of(Map.of("title", "Ubik", "rank", 1L), Map.of("title", "Dune", "rank", 2L)),
          store.query(
              "WITH modern AS (SELECT * FROM books WHERE year > ?) SELECT title, RANK() OVER"
                  + " (ORDER BY year DESC) AS rank FROM modern",
              1900));
      Assertions.assertEquals(
          List.of(Map.of("n", 2L)), store.query("SELECT json_array_length(?) AS n", "[1, 2]"));
      Assertions.assertEquals(
          List.of(Map.of("id", 4L)),
          store.query("INSERT INTO books (title) VALUES ('Solaris') RETURNING id"));
      Assertions.assertEquals(2, store.execute("UPDATE books SET price = 1 WHERE year < 1966"));
    }
  }

  @Test
  void constraintsAndBadSqlFailApart() {
    try (Store store = Store.inMemory()) {
      store.executeScript(SCHEMA);
      store.execute("INSERT INTO books (title) VALUES ('Dune')");
      ConstraintViolatedException unique =
          Assertions.assertThrows(
              ConstraintViolatedException.class,
              () -> store.execute("INSERT INTO books (title) VALUES ('Dune')"));
      Assertions.assertEquals(2067, unique.getCode());
      Assertions.assertThrows(
          ConstraintViolatedException.class,
          () -> store.execute("INSERT INTO books (year) VALUES (1)"));
      StoreFailedException syntax =
          Assertions.assertThrows(StoreFailedException.class, () -> store.query("SELEC 1"));
      Assertions.assertEquals(1, syntax.getCode());
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> store.query("SELECT 1; SELECT 2"));
      Assertions.assertThrows(IllegalArgumentException.class, () -> store.query("SELECT ? + ?", 1));
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> store.query("SELECT :a", Map.of("b", 1)));
    }
  }

  @Test
  void transactionHappensWholeOrNotAtAll() {
    try (Store store = Store.inMemory()) {
      store.executeScript(SCHEMA);
      Assertions.assertThrows(
          ConstraintViolatedException.class,
          () ->
              store.transaction(
                  inside -> {
                    inside.execute("INSERT INTO books (title) VALUES ('Dune')");
                    inside.execute("INSERT INTO books (title) VALUES ('Dune')");
                  }));
      Assertions.assertEquals(
          List.of(Map.of("n", 0L)), store.query("SELECT count(*) AS n FROM books"));

      long id =
          store.inTransaction(
              inside -> {
                inside.execute("INSERT INTO books (title) VALUES ('Emma')");
                long emma = inside.lastInsertRowId();
                Assertions.assertThrows(
                    IllegalStateException.class,
                    () ->
                        inside.transaction(
                            nested -> {
                              nested.execute("INSERT INTO books (title) VALUES ('Ubik')");
                              throw new IllegalStateException("undo the nested one");
                            }));
                return emma;
              });
      Assertions.assertEquals(
          List.of(Map.of("id", id, "title", "Emma")), store.query("SELECT id, title FROM books"));
    }
  }

  @Test
  void theFileKeepsTheDataAndClosingIsFinal(@TempDir Path directory) {
    Path file = directory.resolve("nested/store.sqlite");
    try (Store store = Store.open(file)) {
      store.executeScript("CREATE TABLE notes (text TEXT); INSERT INTO notes VALUES ('kept');");
    }
    Store reopened = Store.open(file);
    try (reopened) {
      Assertions.assertEquals(
          List.of(Map.of("text", "kept")), reopened.query("SELECT text FROM notes"));
      Assertions.assertEquals(
          List.of(Map.of("journal_mode", "wal")), reopened.query("PRAGMA journal_mode"));
    }
    Assertions.assertThrows(IllegalStateException.class, () -> reopened.query("SELECT 1"));
    Assertions.assertTrue(
        Arrays.asList(directory.resolve("nested").toFile().list()).contains("store.sqlite"));
  }
}
