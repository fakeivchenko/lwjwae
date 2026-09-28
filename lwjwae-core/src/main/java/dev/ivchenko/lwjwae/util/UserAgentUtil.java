package dev.ivchenko.lwjwae.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import lombok.experimental.UtilityClass;

/**
 * The user agent of every web view: the one of the engine, and after it the application and lwjwae
 * with its version, such as {@code ... Safari/605.1.15 Notes lwjwae/0.7.1}.
 *
 * <p>The string of the engine stays whole, so a site that looks for WebKit, Chrome, or Safari in it
 * still finds them, and a server of the application tells its own windows from browsers by the
 * token of lwjwae. The name of the application comes from {@link
 * dev.ivchenko.lwjwae.ApplicationParameters#name()}, reduced to the characters that a product token
 * may hold; an application without a name, or a name of none of them, adds lwjwae alone.
 */
@UtilityClass
public class UserAgentUtil {
  private final String VERSION = UserAgentUtil.readVersion();

  /** The version of lwjwae, as the build wrote it, or {@code unknown} without it. */
  public String version() {
    return VERSION;
  }

  /** {@code engine} and, after a space, {@link #suffix}. */
  public String of(String engine, String applicationName) {
    String suffix = UserAgentUtil.suffix(applicationName);
    return engine == null || engine.isBlank() ? suffix : engine.strip() + " " + suffix;
  }

  /** The tokens that go after the user agent of the engine: the application, then lwjwae. */
  public String suffix(String applicationName) {
    String product = UserAgentUtil.token(applicationName);
    String library = "lwjwae/" + VERSION;
    return product.isEmpty() ? library : product + " " + library;
  }

  /**
   * {@code name} as a product token of HTTP: a space or any other character that a token can't hold
   * becomes a hyphen, and hyphens at the ends go.
   */
  String token(String name) {
    if (name == null) {
      return "";
    }
    StringBuilder token = new StringBuilder();
    for (char character : name.strip().toCharArray()) {
      boolean allowed =
          character < 0x7F
              && (Character.isLetterOrDigit(character)
                  || "!#$%&'*+-.^_`|~".indexOf(character) >= 0);
      token.append(allowed ? character : '-');
    }
    return token.toString().replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
  }

  private String readVersion() {
    try (InputStream stream =
        UserAgentUtil.class.getResourceAsStream("/dev/ivchenko/lwjwae/version.properties")) {
      if (stream == null) {
        return "unknown";
      }
      Properties properties = new Properties();
      properties.load(stream);
      return properties.getProperty("version", "unknown");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
