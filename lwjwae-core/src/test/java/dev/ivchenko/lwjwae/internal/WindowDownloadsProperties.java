package dev.ivchenko.lwjwae.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.junit.jupiter.api.Assertions;

/**
 * A name that a server suggests for a download, which a server may choose to harm, becomes a file
 * in the downloads folder and nowhere else, on every file system.
 */
class WindowDownloadsProperties {
  private Path directory;

  @BeforeTry
  void createDirectory() throws IOException {
    this.directory = Files.createTempDirectory("lwjwae-downloads");
  }

  @AfterTry
  void deleteDirectory() throws IOException {
    try (Stream<Path> files = Files.walk(this.directory)) {
      for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
        WindowDownloads.release(file, false);
        Files.delete(file);
      }
    }
  }

  @Property(tries = 300)
  void suggestedNameBecomesFileInTheFolder(@ForAll String suggested) throws IOException {
    Path claimed = WindowDownloads.unique(this.directory, suggested);

    Assertions.assertEquals(this.directory.toAbsolutePath().normalize(), claimed.getParent());
    Assertions.assertTrue(Files.isRegularFile(claimed), "claimed as a file: " + claimed);
  }

  @Property
  void nameFitsEveryFileSystem(@ForAll String suggested) {
    String name = WindowDownloads.fileName(suggested);

    Assertions.assertFalse(name.isEmpty());
    Assertions.assertTrue(name.getBytes(StandardCharsets.UTF_8).length <= 255, name);
    Assertions.assertTrue(name.chars().noneMatch(c -> c < 0x20 || "/\\<>:\"|?*".indexOf(c) >= 0));
    Assertions.assertFalse(name.endsWith(".") || name.endsWith(" "), name);
  }
}
