package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationBackendProvider;
import dev.ivchenko.lwjwae.ApplicationParameters;

/** A supported provider of the default priority, so it loses to {@link FakePreferredProvider}. */
public class FakeFallbackProvider implements ApplicationBackendProvider {
  @Override
  public String name() {
    return "fake-fallback";
  }

  @Override
  public boolean isSupported() {
    return FakeProviders.supported;
  }

  @Override
  public ApplicationBackend create(ApplicationParameters parameters) {
    throw new AssertionError("The lower-priority provider must not be chosen");
  }
}
