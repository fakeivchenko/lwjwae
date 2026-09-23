package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import lombok.Builder;

/**
 * What an application starts with: the settings that hold for the whole process rather than for one
 * window. A window has its own {@link WindowParameters}.
 *
 * <p>Every component has a usable default, which the canonical constructor applies, so a caller
 * only states what it needs:
 *
 * <pre>{@code
 * ApplicationParameters.builder().codec(new JacksonBridgeCodec()).build()
 * }</pre>
 *
 * @param devServerUrl Where the frontend is served from during development, for example {@code
 *     http://localhost:5173}. When set, {@link Window#loadResource} opens this URL instead of the
 *     bundled files, so a Vite or webpack development server with hot reload drives the windows
 *     while the Java side stays as it ships. The default comes from the {@code lwjwae.devServerUrl}
 *     system property, then from the {@code LWJWAE_DEV_SERVER_URL} environment variable, then
 *     nothing, so switching modes never needs a code change.
 * @param codec The codec behind the typed bridge methods. The default is the first {@link
 *     BridgeCodec} on the classpath. When there is none, the value stays {@code null} and typed
 *     calls fail with a message.
 * @param name The name of the application, as the desktop shows it next to its notifications.
 *     Default: none, and the desktop shows its own placeholder.
 */
@Builder(toBuilder = true)
public record ApplicationParameters(String devServerUrl, BridgeCodec codec, String name) {
  /** The system property that supplies {@link #devServerUrl()} when the builder leaves it unset. */
  public static final String DEV_SERVER_URL_PROPERTY = "lwjwae.devServerUrl";

  /** The environment variable with the same meaning as {@link #DEV_SERVER_URL_PROPERTY}. */
  public static final String DEV_SERVER_URL_VARIABLE = "LWJWAE_DEV_SERVER_URL";

  public ApplicationParameters {
    if (isBlank(devServerUrl)) {
      devServerUrl = System.getProperty(DEV_SERVER_URL_PROPERTY);
    }
    if (isBlank(devServerUrl)) {
      devServerUrl = System.getenv(DEV_SERVER_URL_VARIABLE);
    }
    if (isBlank(devServerUrl)) {
      devServerUrl = null;
    }
    if (codec == null) {
      codec = BridgeCodec.discover().orElse(null);
    }
    if (isBlank(name)) {
      name = null;
    }
  }

  /** Creates parameters with every default. */
  public static ApplicationParameters createDefault() {
    return builder().build();
  }

  /** Checks whether the frontend comes from a development server instead of from the classpath. */
  public boolean isDevelopment() {
    return this.devServerUrl != null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
