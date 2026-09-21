package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.ApplicationBackend;
import dev.ivchenko.lwjwae.ApplicationBackendProvider;
import dev.ivchenko.lwjwae.ApplicationParameters;

/**
 * The provider that discovery must pick: supported, and ranked above {@link FakeFallbackProvider}.
 */
public class FakePreferredProvider implements ApplicationBackendProvider {
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
  public ApplicationBackend create(ApplicationParameters parameters) {
    return new FakeApplicationBackend(parameters);
  }
}
