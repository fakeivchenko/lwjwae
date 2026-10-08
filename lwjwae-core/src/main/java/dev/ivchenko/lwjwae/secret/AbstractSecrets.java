package dev.ivchenko.lwjwae.secret;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * The checks that every backend makes the same way: a key that isn't blank and a value within
 * {@link Secrets#MAXIMUM_SIZE}. A backend reads, writes, and deletes in the store of its platform,
 * under the name of the application.
 */
public abstract class AbstractSecrets implements Secrets {
  private final String service;

  /**
   * Keeps the secrets of the application named {@code service}.
   *
   * @throws IllegalArgumentException If {@code service} is blank.
   */
  protected AbstractSecrets(String service) {
    if (service == null || service.isBlank()) {
      throw new IllegalArgumentException("Secrets need the name of the application");
    }
    this.service = service;
  }

  /** The name of the application, which the secrets belong to. */
  protected final String service() {
    return this.service;
  }

  @Override
  public final Optional<String> get(String key) {
    return Optional.ofNullable(this.read(AbstractSecrets.checkKey(key)));
  }

  @Override
  public final void set(String key, String value) {
    Objects.requireNonNull(value, "value");
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAXIMUM_SIZE) {
      throw new IllegalArgumentException(
          "A secret takes at most " + MAXIMUM_SIZE + " bytes, not " + bytes.length);
    }
    this.write(AbstractSecrets.checkKey(key), value);
  }

  @Override
  public final boolean delete(String key) {
    return this.remove(AbstractSecrets.checkKey(key));
  }

  /** The secret under {@code key}, already checked, or {@code null} if none. */
  protected abstract String read(String key);

  /** Keeps {@code value} under {@code key}, both already checked. */
  protected abstract void write(String key, String value);

  /** Deletes the secret under {@code key}, already checked, and answers whether there was one. */
  protected abstract boolean remove(String key);

  private static String checkKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("A secret has a key that isn't blank");
    }
    return key;
  }
}
