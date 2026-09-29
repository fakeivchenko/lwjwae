package dev.ivchenko.lwjwae.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UserAgentUtilTest {
  @Test
  void applicationAndLibraryFollowTheEngine() {
    String library = "lwjwae/" + UserAgentUtil.version();
    Assertions.assertEquals(
        "Mozilla/5.0 AppleWebKit/605.1.15 Notes " + library,
        UserAgentUtil.of("Mozilla/5.0 AppleWebKit/605.1.15 ", "Notes"));
    Assertions.assertEquals(library, UserAgentUtil.of(null, null));
    Assertions.assertEquals("Engine " + library, UserAgentUtil.of("Engine", "  "));
  }

  @Test
  void nameBecomesProductToken() {
    Assertions.assertEquals("lwjwae-demo", UserAgentUtil.token("lwjwae demo"));
    Assertions.assertEquals("My-App-_2", UserAgentUtil.token(" My (App) _2"));
    Assertions.assertEquals("", UserAgentUtil.token("Заметки"));
    Assertions.assertEquals("Notes", UserAgentUtil.token("Заметки Notes"));
  }

  @Test
  void versionComesFromTheBuild() {
    Assertions.assertNotEquals("unknown", UserAgentUtil.version());
  }
}
