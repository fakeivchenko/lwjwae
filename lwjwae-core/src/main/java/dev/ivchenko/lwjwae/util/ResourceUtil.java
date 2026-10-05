package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
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

  /** A scheme and its colon, as RFC 3986 spells it, of two letters or more to leave out C:. */
  private final Pattern URL_SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]+:");

  /**
   * Whether {@code target} is a URL, with a scheme such as {@code https:} or {@code file:}, rather
   * than the path of a resource, such as {@code app/index.html}, which has none.
   */
  public boolean isUrl(String target) {
    return URL_SCHEME.matcher(target).lookingAt();
  }

  /** Returns the URL for {@code path}, for example {@code app://local/app/index.html}. */
  public String url(String path) {
    return "%s://%s/%s".formatted(SCHEME, HOST, ResourceUtil.normalize(path));
  }

  /**
   * The resources of this size or larger go to the page as a stream, from a thread of their own,
   * see {@link #stream}; smaller ones are read in one step, which costs less than a thread and a
   * pipe.
   */
  public final long STREAM_THRESHOLD = 1024 * 1024;

  private final int CHUNK_SIZE = 64 * 1024;

  /**
   * The classpath resource that the path of a request to the scheme names: the path without its
   * query or fragment, percent-decoded, without the leading slash. An engine hands the path the way
   * the URL spells it, so a file named {@code my page.html} arrives as {@code my%20page.html}.
   */
  public String servedPath(String urlPath) {
    int end = urlPath.length();
    int query = urlPath.indexOf('?');
    if (query >= 0) {
      end = query;
    }
    int fragment = urlPath.indexOf('#');
    if (fragment >= 0 && fragment < end) {
      end = fragment;
    }
    // A plus is a plus in a path; URLDecoder would make it a space.
    String decoded =
        URLDecoder.decode(urlPath.substring(0, end).replace("+", "%2B"), StandardCharsets.UTF_8);
    return ResourceUtil.normalize(decoded);
  }

  /**
   * The size of the resource at {@code path} that a page asked for, or -1 when the classpath
   * doesn't say. The code of the application, its {@code .class} files, and {@code META-INF} are no
   * page's business, so they count as missing.
   *
   * <p>So does a path with an empty, {@code .}, or {@code ..} segment, or a backslash: a class
   * loader that reads a directory resolves those, so {@code app/../META-INF/x}, which a page spells
   * {@code app%2F..%2FMETA-INF%2Fx} past the URL parser of the engine, would reach what the check
   * keeps out.
   *
   * @throws ResourceNotFoundException If no such resource exists, or it isn't for a page.
   */
  public long servedSize(String path) {
    String normalized = ResourceUtil.normalize(path);
    URL resource =
        ResourceUtil.isForPage(normalized) ? ResourceUtil.loader().getResource(normalized) : null;
    if (resource == null) {
      throw new ResourceNotFoundException("No classpath resource: " + normalized);
    }
    try {
      return resource.openConnection().getContentLengthLong();
    } catch (IOException _) {
      return -1;
    }
  }

  /**
   * Answers {@code exchange} with the resource at {@code path}, part by part, from a virtual thread
   * of its own: the thread of the engine hands the request over and goes on, however large the file
   * is and however long the jar takes to inflate it.
   */
  public void stream(RpcExchange exchange, String path) {
    String normalized = ResourceUtil.normalize(path);
    Thread.ofVirtual()
        .name("lwjwae-resource")
        .start(
            () -> {
              try (InputStream stream = ResourceUtil.loader().getResourceAsStream(normalized)) {
                if (stream == null) {
                  exchange.reply(404, Map.of(), new byte[0]);
                  return;
                }
                exchange.respond(200, Map.of("Content-Type", MimeTypeUtil.of(normalized)));
                byte[] chunk = new byte[CHUNK_SIZE];
                int read;
                while ((read = stream.read(chunk)) > 0) {
                  if (!exchange.write(Arrays.copyOf(chunk, read))) {
                    break;
                  }
                }
                exchange.end();
              } catch (Throwable t) {
                ThrowableUtil.report(t);
              }
            });
  }

  /**
   * Loads {@code path} (for example {@code "app/index.html"}) from the classpath.
   *
   * @throws ResourceNotFoundException If no such resource exists.
   * @throws UncheckedIOException If the resource exists but can't be read.
   */
  public byte[] read(String path) {
    String normalized = ResourceUtil.normalize(path);
    try (InputStream stream = ResourceUtil.loader().getResourceAsStream(normalized)) {
      if (stream == null) {
        throw new ResourceNotFoundException("No classpath resource: " + normalized);
      }
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read classpath resource: " + normalized, e);
    }
  }

  /** Whether a page may read the resource at {@code path}, see {@link #servedSize}. */
  private boolean isForPage(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".class") || lower.startsWith("meta-inf/") || path.indexOf('\\') >= 0) {
      return false;
    }
    String[] segments = path.split("/", -1);
    for (int index = 0; index < segments.length; index++) {
      String segment = segments[index];
      // A trailing slash, the request for a directory, leaves the last segment empty.
      boolean empty = segment.isEmpty() && index < segments.length - 1;
      if (empty || segment.equals(".") || segment.equals("..")) {
        return false;
      }
    }
    return true;
  }

  private ClassLoader loader() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    return loader == null ? ResourceUtil.class.getClassLoader() : loader;
  }

  private String normalize(String path) {
    return path.startsWith("/") ? path.substring(1) : path;
  }
}
