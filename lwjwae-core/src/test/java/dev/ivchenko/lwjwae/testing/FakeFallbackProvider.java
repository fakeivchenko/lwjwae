package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.BackendProvider;

/** A supported provider of the default priority, so it loses to {@link FakePreferredProvider}. */
public class FakeFallbackProvider implements BackendProvider {
  @Override
  public String name() {
    return "fake-fallback";
  }

  @Override
  public boolean isSupported() {
    return FakeProviders.supported;
  }

  @Override
  public Application create(ApplicationParameters parameters) {
    throw new AssertionError("The lower-priority provider must not be chosen");
  }
}
