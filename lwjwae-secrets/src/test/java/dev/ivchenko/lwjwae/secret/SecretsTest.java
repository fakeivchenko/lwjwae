package dev.ivchenko.lwjwae.secret;

import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.testing.FakeApplication;
import dev.ivchenko.lwjwae.testing.Tags;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * What the secrets of an application do on the platform of the test: they go into the store of
 * credentials of the system and come back, a set replaces, a delete removes, and two applications
 * don't see each other's keys. Every test uses keys of its own and deletes them, so a run leaves
 * nothing in the store of the user.
 *
 * <p>The tests run with the display tests, where the session has a store: on Linux, an unlocked
 * GNOME Keyring that {@code scripts/linux/with-secret-service.sh} starts.
 */
@Tag(Tags.DISPLAY)
@Timeout(60)
class SecretsTest {
  /** A fake application with {@code parameters}, and this module started. */
  private static FakeApplication application(ApplicationParameters parameters) {
    return FakeApplication.withModules(parameters);
  }

  private static ApplicationParameters named(String name) {
    return ApplicationParameters.builder().name(name).build();
  }

  /** Skips a test where the session has no store of credentials that a test can use. */
  private void assumeSecretStore() {
    try (FakeApplication application = SecretsTest.application(SecretsTest.named("lwjwae-test"))) {
      Secrets.of(application).get("probe");
    } catch (SecretStoreFailedException | UnsupportedOperationException e) {
      Assumptions.abort("No store of credentials to use: " + e.getMessage());
    }
  }

  @Test
  void secretGoesInComesBackIsReplacedAndGoes() {
    this.assumeSecretStore();
    String key = "contract-" + UUID.randomUUID();
    try (FakeApplication application = SecretsTest.application(SecretsTest.named("lwjwae-test"))) {
      Secrets secrets = Secrets.of(application);
      try {
        Assertions.assertEquals(Optional.empty(), secrets.get(key));
        secrets.set(key, "first ✓ пароль");
        Assertions.assertEquals(Optional.of("first ✓ пароль"), secrets.get(key));
        secrets.set(key, "second");
        Assertions.assertEquals(Optional.of("second"), secrets.get(key), "a set replaces");
        secrets.set(key, "");
        Assertions.assertEquals(Optional.of(""), secrets.get(key), "an empty secret is one");
        Assertions.assertTrue(secrets.delete(key));
        Assertions.assertEquals(Optional.empty(), secrets.get(key));
        Assertions.assertFalse(secrets.delete(key), "nothing left to delete");
      } finally {
        secrets.delete(key);
      }
    }
  }

  @Test
  void secretTakesTheLargestValueAndNoLarger() {
    this.assumeSecretStore();
    String key = "contract-" + UUID.randomUUID();
    try (FakeApplication application = SecretsTest.application(SecretsTest.named("lwjwae-test"))) {
      Secrets secrets = Secrets.of(application);
      try {
        String largest = "x".repeat(Secrets.MAXIMUM_SIZE);
        secrets.set(key, largest);
        Assertions.assertEquals(Optional.of(largest), secrets.get(key));
        Assertions.assertThrows(
            IllegalArgumentException.class, () -> secrets.set(key, largest + "x"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> secrets.get(" "));
      } finally {
        secrets.delete(key);
      }
    }
  }

  @Test
  void applicationsKeepTheirSecretsApart() {
    this.assumeSecretStore();
    String key = "contract-" + UUID.randomUUID();
    try (FakeApplication first = SecretsTest.application(SecretsTest.named("lwjwae-test-a"));
        FakeApplication second = SecretsTest.application(SecretsTest.named("lwjwae-test-b"))) {
      try {
        Secrets.of(first).set(key, "of the first");
        Assertions.assertEquals(Optional.empty(), Secrets.of(second).get(key));
        Secrets.of(second).set(key, "of the second");
        Assertions.assertEquals(Optional.of("of the first"), Secrets.of(first).get(key));
      } finally {
        Secrets.of(first).delete(key);
        Secrets.of(second).delete(key);
      }
    }
  }

  @Test
  void applicationWithoutNameHasNoSecrets() {
    try (FakeApplication application =
        FakeApplication.withModules(ApplicationParameters.builder().build())) {
      Assertions.assertThrows(IllegalStateException.class, () -> Secrets.of(application));
    }
  }
}
