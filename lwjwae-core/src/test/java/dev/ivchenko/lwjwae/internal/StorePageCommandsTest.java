package dev.ivchenko.lwjwae.internal;

import dev.ivchenko.lwjwae.exception.StoreFailedException;
import dev.ivchenko.lwjwae.store.Store;
import dev.ivchenko.lwjwae.util.JsonUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorePageCommandsTest {
  @Test
  void pageMayNotAttachOrCopyTheStoreToAnotherFile(@TempDir Path directory) {
    try (Store store = Store.open(directory.resolve("store.db"))) {
      store.executeScript("CREATE TABLE notes (title TEXT)");
      Path attached = directory.resolve("attached.db");
      Path copy = directory.resolve("copy.db");

      Assertions.assertThrows(
          StoreFailedException.class,
          () ->
              StorePageCommandsTest.run(
                  store, "script", "ATTACH DATABASE '" + attached + "' AS x"));
      Assertions.assertThrows(
          StoreFailedException.class,
          () -> StorePageCommandsTest.run(store, "execute", "VACUUM INTO '" + copy + "'"));

      Assertions.assertFalse(Files.exists(attached), "no file opened");
      Assertions.assertFalse(Files.exists(copy), "no copy written");
    }
  }

  @Test
  void pageMayNotChangeHowSqliteWritesButMayUseOtherPragmas(@TempDir Path directory) {
    try (Store store = Store.open(directory.resolve("store.db"))) {
      store.executeScript("CREATE TABLE notes (title TEXT)");

      for (String pragma : List.of("PRAGMA writable_schema = 1", "PRAGMA journal_mode = DELETE")) {
        Assertions.assertThrows(
            StoreFailedException.class,
            () -> StorePageCommandsTest.run(store, "execute", pragma),
            pragma);
      }
      StorePageCommandsTest.run(store, "execute", "PRAGMA user_version = 3");
      Assertions.assertEquals(
          "[{\"user_version\":3}]",
          StorePageCommandsTest.run(store, "query", "PRAGMA user_version"));
      Assertions.assertTrue(
          StorePageCommandsTest.run(store, "query", "PRAGMA table_info(notes)").contains("title"));
    }
  }

  @Test
  void javaStillReachesWhatThePageMayNot(@TempDir Path directory) {
    try (Store store = Store.open(directory.resolve("store.db"))) {
      store.executeScript("CREATE TABLE notes (title TEXT)");
      Assertions.assertThrows(
          StoreFailedException.class,
          () -> StorePageCommandsTest.run(store, "execute", "PRAGMA writable_schema = 1"));

      Path copy = directory.resolve("copy.db");
      store.execute("VACUUM INTO ?", copy.toString());

      Assertions.assertTrue(Files.exists(copy), "the hold of the page ended with its command");
    }
  }

  private static String run(Store store, String op, String sql) {
    return StorePageCommands.run(store, JsonUtil.write(Map.of("op", op, "sql", sql)));
  }
}
