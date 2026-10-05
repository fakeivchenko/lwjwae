package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.exception.SecretStoreFailedException;
import dev.ivchenko.lwjwae.macos.binding.Security;
import dev.ivchenko.lwjwae.secret.AbstractSecrets;
import java.nio.charset.StandardCharsets;

/**
 * The secrets of an application in the keychain of macOS, as generic passwords with the name of the
 * application as the service and the key as the account.
 *
 * <p>The login keychain, which the session of the user unlocks, keeps them, and Keychain Access
 * lists them as {@code APPLICATION: KEY}. The application that wrote a password reads it back
 * without a prompt; another one asks the user first.
 */
public class MacSecrets extends AbstractSecrets {
  /**
   * Keeps the secrets of the application named {@code service}.
   *
   * @throws IllegalArgumentException If {@code service} is blank.
   */
  public MacSecrets(String service) {
    super(service);
  }

  @Override
  protected String read(String key) {
    try {
      byte[] data = Security.findPassword(this.service(), key);
      return data == null ? null : new String(data, StandardCharsets.UTF_8);
    } catch (IllegalStateException e) {
      throw new SecretStoreFailedException("The keychain failed: " + e.getMessage());
    }
  }

  @Override
  protected void write(String key, String value) {
    try {
      Security.savePassword(
          this.service(), key, this.service() + ": " + key, value.getBytes(StandardCharsets.UTF_8));
    } catch (IllegalStateException e) {
      throw new SecretStoreFailedException("The keychain failed: " + e.getMessage());
    }
  }

  @Override
  protected boolean remove(String key) {
    try {
      return Security.deletePassword(this.service(), key);
    } catch (IllegalStateException e) {
      throw new SecretStoreFailedException("The keychain failed: " + e.getMessage());
    }
  }
}
