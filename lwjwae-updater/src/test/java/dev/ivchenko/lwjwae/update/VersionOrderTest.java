package dev.ivchenko.lwjwae.update;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VersionOrderTest {
  @Test
  void versionsGoInTheOrderOfSemanticVersioning() {
    List<String> ordered =
        List.of(
            "0.9",
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0",
            "1.0.1",
            "1.9.2",
            "1.10.0",
            "2");
    for (int first = 0; first < ordered.size(); first++) {
      for (int second = 0; second < ordered.size(); second++) {
        Assertions.assertEquals(
            Integer.signum(Integer.compare(first, second)),
            Integer.signum(VersionOrder.compare(ordered.get(first), ordered.get(second))),
            ordered.get(first) + " against " + ordered.get(second));
      }
    }
  }

  @Test
  void missingPartsAreZeroAndBuildMetadataDoesNotCount() {
    Assertions.assertEquals(0, VersionOrder.compare("1.2", "1.2.0"));
    Assertions.assertEquals(0, VersionOrder.compare("1.2.0+build.7", "1.2.0"));
    Assertions.assertTrue(VersionOrder.compare("1.2.0-SNAPSHOT", "1.2.0") < 0);
  }

  @Test
  void versionTagPrefixDoesNotCount() {
    Assertions.assertEquals(0, VersionOrder.compare("v1.2.0", "1.2.0"));
    Assertions.assertTrue(VersionOrder.compare("v1.2.0", "1.3.0") < 0);
    Assertions.assertTrue(VersionOrder.compare("1.4.0", "V1.3.0") > 0);
  }
}
