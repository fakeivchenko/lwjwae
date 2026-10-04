package dev.ivchenko.lwjwae.util;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FileDropUtilTest {
  @Test
  void localFileUrisBecomePathsWithTheirEscapesDecoded() {
    List<Path> paths =
        FileDropUtil.pathsOfUris(
            List.of(
                "file:///tmp/a%20file.txt",
                "file:///tmp/odd%20%231%20%26%20100%25%20name.txt",
                "file:///tmp/%D0%BA%D0%B8%D1%80.txt",
                "  file:///tmp/padded.txt\r"));
    Assertions.assertEquals(
        List.of(
            Path.of("/tmp/a file.txt"),
            Path.of("/tmp/odd #1 & 100% name.txt"),
            Path.of("/tmp/кир.txt"),
            Path.of("/tmp/padded.txt")),
        paths);
  }

  @Test
  void whatIsNotAFileOfThisMachineIsLeftOut() {
    Assertions.assertEquals(
        List.of(Path.of("/tmp/kept.txt")),
        FileDropUtil.pathsOfUris(
            List.of(
                "https://example.com/page.html",
                "file://otherhost/share/file.txt",
                "not a uri at all",
                "mailto:someone@example.com",
                "",
                "file:///tmp/kept.txt")));
    Assertions.assertEquals(List.of(), FileDropUtil.pathsOfUris(List.of()));
  }
}
