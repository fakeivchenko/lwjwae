package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.BackendProvider;

/**
 * The provider that discovery must pick: supported, and ranked above {@link FakeFallbackProvider}.
 */
public class FakePreferredProvider implements BackendProvider {
  @Override
  public String name() {
    return "fake-preferred";
  }

  @Override
  public boolean isSupported() {
    return FakeProviders.supported;
  }

  @Override
  public int priority() {
    return 10;
  }

  @Override
  public Application create(ApplicationParameters parameters) {
    return new FakeApplication(parameters);
  }
}
