package dev.ivchenko.lwjwae.secret;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.secret.internal.SecretsModule;
import java.util.Optional;

/**
 * Secrets of the application, such as tokens and passwords, in the store of credentials of the
 * system: the Credential Manager on Windows, the keychain on macOS, and the Secret Service on
 * Linux.
 *
 * <pre>{@code
 * Secrets secrets = Secrets.of(application);
 * secrets.set("api-token", token);
 * Optional<String> saved = secrets.get("api-token");
 * secrets.delete("api-token");
 * }</pre>
 *
 * <p>The system encrypts the secrets for the user and keeps them out of reach of other users, which
 * a file in the data directory or a row of the store can't do. Every secret belongs to the
 * application by its name, so two applications don't see each other's keys.
 *
 * <p>A call blocks until the system answers, and the system may first ask the user to unlock the
 * store, so call it off the UI thread. A value takes at most {@value #MAXIMUM_SIZE} bytes in UTF-8:
 * the limit of the Credential Manager, kept on every platform so that what works on one works on
 * all.
 *
 * <p>The secrets belong to the {@code lwjwae-secrets} module, which joins an application when it is
 * on the class path or the module path, and binds the store of each platform itself, so that it
 * needs no backend of a toolkit.
 */
public interface Secrets {
  /** The largest value, in bytes of UTF-8. */
  int MAXIMUM_SIZE = 2560;

  /**
   * The secrets of {@code application}, under {@link
   * dev.ivchenko.lwjwae.ApplicationParameters#name()}.
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: Generic credentials of the Credential Manager, named {@code APPLICATION/KEY}.
   *   <li>macOS: Generic passwords of the login keychain, with the application as the service and
   *       the key as the account.
   *   <li>Linux, GTK 3: Passwords of the Secret Service, GNOME Keyring or KWallet, through
   *       libsecret, with the application and the key as attributes.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @throws IllegalStateException If the application has no name, or is closed.
   * @throws UnsupportedOperationException If this platform has no store of credentials that the
   *     module binds.
   */
  static Secrets of(Application application) {
    return application.module(SecretsModule.class).secrets();
  }

  /**
   * The secret under {@code key}, or empty if none.
   *
   * @throws IllegalArgumentException If {@code key} is blank.
   * @throws SecretStoreFailedException If the system refused, for example because the user didn't
   *     unlock the store.
   */
  Optional<String> get(String key);

  /**
   * Keeps {@code value} under {@code key}, in place of the secret there, if any.
   *
   * @throws IllegalArgumentException If {@code key} is blank, or {@code value} takes more than
   *     {@link #MAXIMUM_SIZE} bytes in UTF-8.
   * @throws SecretStoreFailedException If the system refused.
   */
  void set(String key, String value);

  /**
   * Deletes the secret under {@code key}.
   *
   * @return True if there was one; false otherwise.
   * @throws IllegalArgumentException If {@code key} is blank.
   * @throws SecretStoreFailedException If the system refused.
   */
  boolean delete(String key);
}
