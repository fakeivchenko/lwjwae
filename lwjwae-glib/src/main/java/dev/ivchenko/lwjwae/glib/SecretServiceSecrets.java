package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.exception.SecretStoreFailedException;
import dev.ivchenko.lwjwae.glib.binding.Libsecret;
import dev.ivchenko.lwjwae.secret.AbstractSecrets;

/**
 * The secrets of an application in the Secret Service of the desktop, through libsecret: GNOME
 * Keyring, KWallet, or another service of the protocol. Both Linux backends share it.
 *
 * <p>A secret is a password of the default collection, which the service unlocks with the session
 * of the user, found by two attributes, the name of the application and the key, under a schema of
 * lwjwae, so that a manager of passwords such as Seahorse shows it as {@code APPLICATION: KEY}.
 */
public class SecretServiceSecrets extends AbstractSecrets {
  /**
   * Keeps the secrets of the application named {@code service}.
   *
   * @throws IllegalArgumentException If {@code service} is blank.
   */
  public SecretServiceSecrets(String service) {
    super(service);
  }

  @Override
  protected String read(String key) {
    SecretServiceSecrets.requireLibsecret();
    try {
      return Libsecret.lookup(this.service(), key);
    } catch (IllegalStateException e) {
      throw new SecretStoreFailedException("The Secret Service failed: " + e.getMessage());
    }
  }

  @Override
  protected void write(String key, String value) {
    SecretServiceSecrets.requireLibsecret();
    try {
      Libsecret.store(this.service(), key, this.service() + ": " + key, value);
    } catch (IllegalStateException e) {
      throw new SecretStoreFailedException("The Secret Service failed: " + e.getMessage());
    }
  }

  @Override
  protected boolean remove(String key) {
    SecretServiceSecrets.requireLibsecret();
    try {
      return Libsecret.clear(this.service(), key);
    } catch (IllegalStateException e) {
      throw new SecretStoreFailedException("The Secret Service failed: " + e.getMessage());
    }
  }

  private static void requireLibsecret() {
    if (!Libsecret.isAvailable()) {
      throw new SecretStoreFailedException(
          "libsecret isn't installed: install libsecret-1-0, or libsecret on Fedora and Arch");
    }
  }
}
