package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationBackendProvider;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests backend discovery without opening a window, so the tests also pass on a headless machine.
 *
 * <p>Each backend module states whether it's the one that this machine should select. A module for
 * another platform still checks that it's registered and that it steps aside.
 */
public abstract class BackendSelectionContractTest {
  /** Returns the provider class that this module registers in {@code META-INF/services}. */
  protected abstract Class<? extends ApplicationBackendProvider> providerType();

  /** Returns the {@link ApplicationBackendProvider#name()} of the provider. */
  protected abstract String providerName();

  /** Checks whether the machine that runs the tests is the platform of this module. */
  protected abstract boolean isThisPlatform();

  @Test
  void providerIsOnTheServicePath() {
    List<ApplicationBackendProvider> providers = Application.providers();
    Assertions.assertTrue(
        providers.stream().anyMatch(this.providerType()::isInstance),
        this.providerType().getSimpleName()
            + " is not registered in META-INF/services: "
            + providers);
  }

  @Test
  void providerAnswersForItsOwnPlatformOnly() {
    ApplicationBackendProvider provider =
        Application.providers().stream()
            .filter(this.providerType()::isInstance)
            .findFirst()
            .orElseThrow();
    Assertions.assertEquals(this.isThisPlatform(), provider.isSupported());
    if (this.isThisPlatform()) {
      Assertions.assertEquals(this.providerName(), Application.provider().orElseThrow().name());
    } else {
      String reason = provider.unsupportedReason();
      Assertions.assertFalse(reason.isBlank(), "An unsupported provider must say why");
      Assertions.assertFalse(
          reason.endsWith("."), "The reason is one clause of a larger message: " + reason);
    }
  }
}
