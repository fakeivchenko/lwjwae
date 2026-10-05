package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.exception.SecretStoreFailedException;
import dev.ivchenko.lwjwae.secret.AbstractSecrets;
import dev.ivchenko.lwjwae.windows.binding.Advapi32;
import java.nio.charset.StandardCharsets;

/**
 * The secrets of an application in the Credential Manager of Windows, as generic credentials named
 * {@code APPLICATION/KEY}.
 *
 * <p>Windows encrypts them with the key of the user and keeps them for this user on this machine,
 * across logons. The Credential Manager of the Control Panel lists them under Windows Credentials,
 * with the key as the user name. The calls are synchronous and work from any thread.
 */
public class WindowsSecrets extends AbstractSecrets {
  /**
   * Keeps the secrets of the application named {@code service}.
   *
   * @throws IllegalArgumentException If {@code service} is blank.
   */
  public WindowsSecrets(String service) {
    super(service);
  }

  @Override
  protected String read(String key) {
    try {
      byte[] blob = Advapi32.readCredential(this.target(key));
      return blob == null ? null : new String(blob, StandardCharsets.UTF_8);
    } catch (IllegalStateException e) {
      throw new SecretStoreFailedException(e.getMessage());
    }
  }

  @Override
  protected void write(String key, String value) {
    try {
      Advapi32.writeCredential(this.target(key), key, value.getBytes(StandardCharsets.UTF_8));
    } catch (IllegalStateException e) {
      throw new SecretStoreFailedException(e.getMessage());
    }
  }

  @Override
  protected boolean remove(String key) {
    try {
      return Advapi32.deleteCredential(this.target(key));
    } catch (IllegalStateException e) {
      throw new SecretStoreFailedException(e.getMessage());
    }
  }

  private String target(String key) {
    return this.service() + "/" + key;
  }
}
