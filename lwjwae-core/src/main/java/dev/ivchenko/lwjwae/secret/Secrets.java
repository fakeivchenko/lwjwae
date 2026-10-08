package dev.ivchenko.lwjwae.secret;

import java.util.Optional;

/**
 * Secrets of the application, such as tokens and passwords, in the store of credentials of the
 * system: the Credential Manager on Windows, the keychain on macOS, and the Secret Service on
 * Linux.
 *
 * <pre>{@code
 * Secrets secrets = application.secrets();
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
 */
public interface Secrets {
  /** The largest value, in bytes of UTF-8. */
  int MAXIMUM_SIZE = 2560;

  /**
   * The secret under {@code key}, or empty if none.
   *
   * @throws IllegalArgumentException If {@code key} is blank.
   * @throws dev.ivchenko.lwjwae.exception.SecretStoreFailedException If the system refused, for
   *     example because the user didn't unlock the store.
   */
  Optional<String> get(String key);

  /**
   * Keeps {@code value} under {@code key}, in place of the secret there, if any.
   *
   * @throws IllegalArgumentException If {@code key} is blank, or {@code value} takes more than
   *     {@link #MAXIMUM_SIZE} bytes in UTF-8.
   * @throws dev.ivchenko.lwjwae.exception.SecretStoreFailedException If the system refused.
   */
  void set(String key, String value);

  /**
   * Deletes the secret under {@code key}.
   *
   * @return True if there was one; false otherwise.
   * @throws IllegalArgumentException If {@code key} is blank.
   * @throws dev.ivchenko.lwjwae.exception.SecretStoreFailedException If the system refused.
   */
  boolean delete(String key);
}
