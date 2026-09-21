package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationBackendProvider;
import dev.ivchenko.lwjwae.ApplicationParameters;

/**
 * The highest-ranked provider of all, but never supported: it must step aside despite its priority.
 */
public class FakeOtherPlatformProvider implements ApplicationBackendProvider {
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
  public ApplicationBackend create(ApplicationParameters parameters) {
    throw new AssertionError("An unsupported provider must not be chosen");
  }
}
