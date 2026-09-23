package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.BackendProvider;

/**
 * The highest-ranked provider of all, but never supported: it must step aside despite its priority.
 */
public class FakeOtherPlatformProvider implements BackendProvider {
  @Override
  public String name() {
    return "fake-other-platform";
  }

  @Override
  public boolean isSupported() {
    return false;
  }

  @Override
  public int priority() {
    return 100;
  }

  @Override
  public Application create(ApplicationParameters parameters) {
    throw new AssertionError("An unsupported provider must not be chosen");
  }
}
