package dev.ivchenko.lwjwae.testing;

import lombok.experimental.UtilityClass;

/** JUnit tag names. The Gradle tasks of a backend module select tests by them. */
@UtilityClass
public class Tags {
  /** Opens a real window, so it needs an X11 or Wayland display. */
  public final String DISPLAY = "display";

  /** Reaches the public internet. Excluded from every default run. */
  public final String NETWORK = "network";
}
