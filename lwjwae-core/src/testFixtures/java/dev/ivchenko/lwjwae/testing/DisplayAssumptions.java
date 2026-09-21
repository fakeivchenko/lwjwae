package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.util.PlatformUtil;
import lombok.experimental.UtilityClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

/**
 * The gate for tests that open a window.
 *
 * <p>Without a display, such a test is skipped, so a developer on a headless machine still gets a
 * passing build. CI must never take that shortcut, because a skipped display test there is a false
 * pass. With {@code -Dlwjwae.requireDisplay=true}, the missing display is a failure instead.
 */
@UtilityClass
public class DisplayAssumptions {
  /** The system property that turns a missing display into a failure instead of a skipped test. */
  public final String REQUIRE_DISPLAY_PROPERTY = "lwjwae.requireDisplay";

  /**
   * Skips the current test when no display is available, or fails it under {@link
   * #REQUIRE_DISPLAY_PROPERTY}.
   */
  public void assumeDisplay() {
    boolean present =
        PlatformUtil.isWindows()
            || PlatformUtil.isMacOs()
            || System.getenv("DISPLAY") != null
            || System.getenv("WAYLAND_DISPLAY") != null;
    if (!present && Boolean.getBoolean(REQUIRE_DISPLAY_PROPERTY)) {
      Assertions.fail(
          "No display, but -D" + REQUIRE_DISPLAY_PROPERTY + "=true: run under xvfb-run");
    }
    Assumptions.assumeTrue(present, "No X11/Wayland display available");
  }
}
