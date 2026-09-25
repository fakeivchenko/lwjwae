package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.experimental.UtilityClass;

/**
 * Reads the files of the application (HTML, CSS, scripts, and images) from the classpath, and
 * defines the URL space that they're served under.
 *
 * <p>Pages are loaded from a custom scheme instead of {@code file://}, so that relative links,
 * {@code fetch}, and module imports resolve the way they resolve on a web server, and so that the
 * same markup works on every backend. WebKitGTK registers the scheme on its web context, WKWebView
 * uses a URL scheme handler, and WebView2 intercepts the request.
 */
@UtilityClass
public class ResourceUtil {
  /** The scheme that the resources of the application are served under. */
  public final String SCHEME = "app";

  /** The authority of that scheme. It's a constant, because no real host is involved. */
  public final String HOST = "local";

  /** Returns the URL for {@code path}, for example {@code app://local/app/index.html}. */
  public String url(String path) {
    return "%s://%s/%s".formatted(SCHEME, HOST, ResourceUtil.normalize(path));
  }

  /**
   * Loads {@code path} (for example {@code "app/index.html"}) from the classpath.
   *
   * @throws ResourceNotFoundException If no such resource exists.
   * @throws UncheckedIOException If the resource exists but can't be read.
   */
  public byte[] read(String path) {
    String normalized = ResourceUtil.normalize(path);
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = ResourceUtil.class.getClassLoader();
    }

    try (InputStream stream = loader.getResourceAsStream(normalized)) {
      if (stream == null) {
        throw new ResourceNotFoundException("No classpath resource: " + normalized);
      }
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read classpath resource: " + normalized, e);
    }
  }

  private String normalize(String path) {
    return path.startsWith("/") ? path.substring(1) : path;
  }
}
