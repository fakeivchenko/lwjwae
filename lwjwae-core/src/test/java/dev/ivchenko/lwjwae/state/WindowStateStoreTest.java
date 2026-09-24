package dev.ivchenko.lwjwae.state;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowStateStoreTest {
  @TempDir Path directory;

  @Test
  void statesOfSeveralWindowsSurviveTogether() {
    WindowStateStore store = new WindowStateStore(this.directory.resolve("app"));
    store.save("main", new SavedWindowState(800, 600, 10, 20, true));
    store.save("tools", new SavedWindowState(300, 200, null, null, false));

    WindowStateStore again = new WindowStateStore(this.directory.resolve("app"));
    Assertions.assertEquals(
        new SavedWindowState(800, 600, 10, 20, true), again.load("main").orElseThrow());
    Assertions.assertEquals(
        new SavedWindowState(300, 200, null, null, false), again.load("tools").orElseThrow());
    Assertions.assertTrue(again.load("missing").isEmpty());
  }

  @Test
  void aDamagedFileIsNoState() throws Exception {
    Files.writeString(this.directory.resolve("window-state.properties"), "main.width=wide\n");
    WindowStateStore store = new WindowStateStore(this.directory);
    Assertions.assertTrue(store.load("main").isEmpty());
    store.save("main", new SavedWindowState(640, 480, null, null, false));
    Assertions.assertEquals(640, store.load("main").orElseThrow().width());
  }
}
