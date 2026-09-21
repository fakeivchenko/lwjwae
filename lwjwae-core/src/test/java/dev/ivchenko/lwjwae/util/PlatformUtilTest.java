package dev.ivchenko.lwjwae.util;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PlatformUtilTest {
  @Test
  void idComesFirstThenTheIdLikeChain() {
    List<String> osRelease =
        List.of(
            "NAME=\"Rocky Linux\"",
            "ID_LIKE=\"rhel centos fedora\"",
            "ID=\"rocky\"",
            "PRETTY_NAME=\"Rocky Linux 9.4\"");
    Assertions.assertEquals(
        List.of("rocky", "rhel", "centos", "fedora"), PlatformUtil.distributionIds(osRelease));
  }

  @Test
  void unquotedValuesAndMissingIdLike() {
    Assertions.assertEquals(
        List.of("arch"), PlatformUtil.distributionIds(List.of("ID=arch", "BUILD_ID=rolling")));
  }

  @Test
  void ignoresBlankAndMalformedLines() {
    Assertions.assertEquals(
        List.of("ubuntu", "debian"),
        PlatformUtil.distributionIds(List.of("", "# comment", "ID=ubuntu", "ID_LIKE=debian")));
  }

  @Test
  void emptyWithoutAnId() {
    Assertions.assertTrue(PlatformUtil.distributionIds(List.of()).isEmpty());
  }
}
