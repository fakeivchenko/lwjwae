package dev.ivchenko.lwjwae.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import lombok.Builder;

/**
 * Where an application looks for its updates, and what it trusts.
 *
 * <p>The Gradle plugin writes them into the application as {@value #RESOURCE}, from the {@code
 * updates} block of the build, so an application that it builds has them without code: {@link
 * #discover()} reads them there.
 *
 * @param manifestUrl The URL of the manifest, a JSON file; its signature is the same URL with
 *     {@code .sig} after it.
 * @param publicKey The Ed25519 key that signs the manifest, as Base64 of its X.509 encoding.
 * @param currentVersion The version that runs, such as {@code 1.3.2}.
 */
@Builder(toBuilder = true)
public record UpdateParameters(URI manifestUrl, String publicKey, String currentVersion) {
  /** The resource that the Gradle plugin writes the parameters into. */
  public static final String RESOURCE = "META-INF/lwjwae/update.properties";

  public UpdateParameters {
    Objects.requireNonNull(manifestUrl, "manifestUrl");
    Objects.requireNonNull(publicKey, "publicKey");
    Objects.requireNonNull(currentVersion, "currentVersion");
  }

  /**
   * The parameters in {@value #RESOURCE}, with the keys {@code manifestUrl}, {@code publicKey}, and
   * {@code version}, or nothing when the application has no such resource.
   *
   * @throws IllegalArgumentException If the resource lacks a key.
   */
  public static Optional<UpdateParameters> discover() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = UpdateParameters.class.getClassLoader();
    }
    try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        return Optional.empty();
      }
      Properties properties = new Properties();
      properties.load(in);
      return Optional.of(
          new UpdateParameters(
              URI.create(UpdateParameters.required(properties, "manifestUrl")),
              UpdateParameters.required(properties, "publicKey"),
              UpdateParameters.required(properties, "version")));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(RESOURCE + " has no " + key);
    }
    return value.strip();
  }
}
