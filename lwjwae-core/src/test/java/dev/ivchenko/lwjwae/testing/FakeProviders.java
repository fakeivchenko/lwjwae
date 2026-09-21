package dev.ivchenko.lwjwae.testing;

import lombok.experimental.UtilityClass;

/**
 * The switch shared by the fake providers registered in {@code META-INF/services} of the test
 * classpath. {@link #supported} turns the two viable providers off together, because a service file
 * can't be edited per test.
 */
@UtilityClass
public class FakeProviders {
  public volatile boolean supported = true;
}
