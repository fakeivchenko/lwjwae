package dev.ivchenko.lwjwae.glib.util;

import dev.ivchenko.lwjwae.dialog.FileType;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FileTypeUtilTest {
  @Test
  void everyLetterMatchesEitherCase() {
    Assertions.assertEquals(
        List.of("*.[pP][nN][gG]", "*.[mM][pP]4"),
        FileTypeUtil.patterns(FileType.of("Media", "PNG", ".mp4")));
    Assertions.assertEquals(List.of("*"), FileTypeUtil.patterns(FileType.of("All")));
  }
}
