package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.event.OpenEvent;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenArgumentUtilTest {
  @Test
  void linksAndExistingFilesCountAndTheRestIsLeftOut(@TempDir Path directory) throws Exception {
    Path note = Files.writeString(directory.resolve("my note #1.txt"), "text");
    OpenEvent request =
        OpenArgumentUtil.of(
            List.of(
                "notes://today?view=list",
                "--verbose",
                "-",
                "",
                "my note #1.txt",
                "missing.txt",
                note.toUri().toString(),
                "mailto:hello@example.com",
                "C:\\nope.txt",
                "not a uri: really"),
            directory);
    Assertions.assertEquals(
        List.of(URI.create("notes://today?view=list"), URI.create("mailto:hello@example.com")),
        request.urls());
    Assertions.assertEquals(List.of(note, note), request.files());
    Assertions.assertFalse(request.isEmpty());
  }

  @Test
  void argumentsOfNothingToOpenMakeAnEmptyRequest() {
    Assertions.assertTrue(
        OpenArgumentUtil.of(List.of("--flag", "nothing-here"), Path.of("/nonexistent")).isEmpty());
  }
}
