package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;
import lombok.Builder;

/**
 * The window that a backend creates.
 *
 * <p>Every component has a usable default, which the canonical constructor applies, so a caller
 * only states what it needs:
 *
 * <pre>{@code
 * ApplicationParameters.builder().title("Docs").width(1280).height(800).build()
 * }</pre>
 *
 * @param title The window title. Default: {@code "Application"}.
 * @param width The initial window width, in pixels. A non-positive value means the default.
 *     Default: {@code 1024}.
 * @param height The initial window height, in pixels. A non-positive value means the default.
 *     Default: {@code 768}.
 * @param x The screen X coordinate that the window opens at, in the units of the platform, or
 *     {@code null} to let the window manager choose. Both {@code x} and {@code y} must be set for
 *     either to count. Wayland ignores them: a client can't place its window there.
 * @param y The screen Y coordinate that the window opens at, measured from the top. See {@code x}.
 * @param centered Whether the window opens in the middle of the screen. Wins over {@code x} and
 *     {@code y}. Default: {@code false}, except on macOS, where every window opens centered.
 * @param url The URL to load after the window exists, or {@code null} to leave the window blank.
 *     This is a convenience for simple cases. {@link Application#create} navigates before it
 *     returns, so to observe a load from its first event, leave this value unset, register the
 *     listener, and call {@link ApplicationBackend#navigate} yourself.
 * @param devServerUrl Where the frontend is served from during development, for example {@code
 *     http://localhost:5173}. When set, {@link ApplicationBackend#loadResource} opens this URL
 *     instead of the bundled files, so a Vite or webpack development server with hot reload drives
 *     the window while the Java side stays as it ships. The default comes from the {@code
 *     lwjwae.devServerUrl} system property, then from the {@code LWJWAE_DEV_SERVER_URL} environment
 *     variable, then nothing, so switching modes never needs a code change.
 * @param codec The codec behind the typed bridge methods. The default is the first {@link
 *     BridgeCodec} on the classpath. When there is none, the value stays {@code null} and typed
 *     calls fail with a message.
 */
@Builder(toBuilder = true)
public record ApplicationParameters(
    String title,
    int width,
    int height,
    Integer x,
    Integer y,
    boolean centered,
    String url,
    String devServerUrl,
    BridgeCodec codec) {
  /** The system property that supplies {@link #devServerUrl()} when the builder leaves it unset. */
  public static final String DEV_SERVER_URL_PROPERTY = "lwjwae.devServerUrl";

  /** The environment variable with the same meaning as {@link #DEV_SERVER_URL_PROPERTY}. */
  public static final String DEV_SERVER_URL_VARIABLE = "LWJWAE_DEV_SERVER_URL";

  private static final String DEFAULT_TITLE = "Application";
  private static final int DEFAULT_WIDTH = 1024;
  private static final int DEFAULT_HEIGHT = 768;

  public ApplicationParameters {
    if (title == null || title.isBlank()) {
      title = DEFAULT_TITLE;
    }
    if (width <= 0) {
      width = DEFAULT_WIDTH;
    }
    if (height <= 0) {
      height = DEFAULT_HEIGHT;
    }
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
    if (x == null || y == null) {
      x = null;
      y = null;
    }
  }

  /**
   * Whether the window opens at {@link #x()}, {@link #y()} rather than where the platform puts it.
   */
  public boolean hasPosition() {
    return this.x != null;
  }

  /**
   * Creates a window with every default: 1024 by 768 pixels, blank, and titled {@code
   * "Application"}.
   */
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
