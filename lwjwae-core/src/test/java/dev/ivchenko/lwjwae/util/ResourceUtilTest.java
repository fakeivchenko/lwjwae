package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ResourceUtilTest {
  @Test
  void buildsAppSchemeUrls() {
    Assertions.assertEquals("app://local/app/index.html", ResourceUtil.url("app/index.html"));
    Assertions.assertEquals("app://local/app/index.html", ResourceUtil.url("/app/index.html"));
  }

  @Test
  void readsClasspathResourcesWithOrWithoutLeadingSlash() {
    String expected = "hello from the classpath\n";
    Assertions.assertEquals(
        expected, new String(ResourceUtil.read("fixtures/hello.txt"), StandardCharsets.UTF_8));
    Assertions.assertEquals(
        expected, new String(ResourceUtil.read("/fixtures/hello.txt"), StandardCharsets.UTF_8));
  }

  @Test
  void missingResourceIsTypedFailure() {
    ResourceNotFoundException failure =
        Assertions.assertThrows(
            ResourceNotFoundException.class, () -> ResourceUtil.read("fixtures/nope.txt"));
    Assertions.assertTrue(failure.getMessage().contains("fixtures/nope.txt"));
  }
}
