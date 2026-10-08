package dev.ivchenko.lwjwae.testing.contract;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.secret.Secrets;
import dev.ivchenko.lwjwae.testing.Tags;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * What every backend does with the secrets of an application: they go into the store of credentials
 * of the system and come back, a set replaces, a delete removes, and two applications don't see
 * each other's keys. Every test uses keys of its own and deletes them, so a run leaves nothing in
 * the store of the user.
 */
@Tag(Tags.DISPLAY)
@Timeout(60)
public abstract class SecretsContractTest extends DisplayContractTest {
  /**
   * Skips a test where the machine has no store of credentials that a test can use, such as a Linux
   * session without a Secret Service. Every test calls it first. The default assumes nothing.
   */
  protected void assumeSecretStore() {}

  private static ApplicationParameters named(String name) {
    return ApplicationParameters.builder().name(name).build();
  }

  @Test
  void secretGoesInComesBackIsReplacedAndGoes() {
    this.assumeSecretStore();
    String key = "contract-" + UUID.randomUUID();
    try (Application application = Application.create(SecretsContractTest.named("lwjwae-test"))) {
      Secrets secrets = application.secrets();
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
    try (Application application = Application.create(SecretsContractTest.named("lwjwae-test"))) {
      Secrets secrets = application.secrets();
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
    try (Application first = Application.create(SecretsContractTest.named("lwjwae-test-a"));
        Application second = Application.create(SecretsContractTest.named("lwjwae-test-b"))) {
      try {
        first.secrets().set(key, "of the first");
        Assertions.assertEquals(Optional.empty(), second.secrets().get(key));
        second.secrets().set(key, "of the second");
        Assertions.assertEquals(Optional.of("of the first"), first.secrets().get(key));
      } finally {
        first.secrets().delete(key);
        second.secrets().delete(key);
      }
    }
  }

  @Test
  void applicationWithoutNameHasNoSecrets() {
    try (Application application = Application.create()) {
      Assertions.assertThrows(IllegalStateException.class, application::secrets);
    }
  }
}
