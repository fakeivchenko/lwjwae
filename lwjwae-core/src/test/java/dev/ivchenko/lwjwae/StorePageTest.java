package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.FakeWindow;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(10)
class StorePageTest {
  @Test
  void thePageRunsSqlOnTheStoreOfTheApplication(@TempDir Path directory) throws Exception {
    ApplicationParameters parameters =
        ApplicationParameters.builder().name("notes").dataDirectory(directory).build();
    try (FakeApplication application = new FakeApplication(parameters)) {
      FakeWindow window = application.openFake();
      window.call(
          1,
          BridgeProtocol.STORE_CALL,
          "{\"op\":\"script\",\"sql\":\"CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT"
              + " UNIQUE, data BLOB)\"}");
      Assertions.assertEquals("", window.awaitReply(1).body());
      window.call(
          2,
          BridgeProtocol.STORE_CALL,
          "{\"op\":\"execute\",\"sql\":\"INSERT INTO notes (text, data) VALUES (?, x'0102')\","
              + "\"params\":[\"a\"]}");
      Assertions.assertEquals("{\"changes\":1,\"lastInsertRowId\":1}", window.awaitReply(2).body());
      window.call(
          3,
          BridgeProtocol.STORE_CALL,
          "{\"op\":\"query\",\"sql\":\"SELECT * FROM notes WHERE id ="
              + " :id\",\"params\":{\"id\":1}}");
      Assertions.assertEquals(
          "[{\"id\":1,\"text\":\"a\",\"data\":\"AQI=\"}]", window.awaitReply(3).body());
      window.call(
          4,
          BridgeProtocol.STORE_CALL,
          "{\"op\":\"transaction\",\"statements\":[{\"sql\":\"INSERT INTO notes (text) VALUES"
              + " ('b') RETURNING id\"},{\"sql\":\"INSERT INTO notes (text) VALUES ('a')\"}]}");
      Assertions.assertEquals(409, window.awaitReply(4).status(), "the second insert repeats a");
      window.call(5, BridgeProtocol.STORE_CALL, "{\"op\":\"query\",\"sql\":\"SELEC 1\"}");
      Assertions.assertEquals(400, window.awaitReply(5).status());
      window.call(6, BridgeProtocol.STORE_CALL, "not json");
      Assertions.assertEquals(400, window.awaitReply(6).status());
      Assertions.assertEquals(
          List.of(Map.of("text", "a")),
          application.store().query("SELECT text FROM notes"),
          "Java and the page share the store, and the transaction left nothing");
    }
    Assertions.assertTrue(directory.resolve("store.sqlite").toFile().isFile());
  }

  @Test
  void anApplicationWithoutDataDirectoryHasNoStore() {
    try (FakeApplication application = new FakeApplication()) {
      Assertions.assertThrows(IllegalStateException.class, application::store);
    }
  }
}
