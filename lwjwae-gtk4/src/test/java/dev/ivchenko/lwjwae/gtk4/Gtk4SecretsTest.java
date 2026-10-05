package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.exception.SecretStoreFailedException;
import dev.ivchenko.lwjwae.glib.SecretServiceSecrets;
import dev.ivchenko.lwjwae.testing.contract.SecretsContractTest;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import org.junit.jupiter.api.Assumptions;

class Gtk4SecretsTest extends SecretsContractTest {
  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isUnixDesktop();
  }

  /** A session without a Secret Service, or with a locked one, has nothing that a test can use. */
  @Override
  protected void assumeSecretStore() {
    try {
      new SecretServiceSecrets("lwjwae-test").get("probe");
    } catch (SecretStoreFailedException e) {
      Assumptions.abort("No Secret Service to use: " + e.getMessage());
    }
  }
}
