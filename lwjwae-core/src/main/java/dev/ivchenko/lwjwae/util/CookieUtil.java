package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.cookie.Cookie;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * Whether a browser sends a cookie with a request, by the rules of RFC 6265.
 *
 * <p>WebKitGTK and WebView2 answer which cookies go to a URL themselves, but the cookie store of
 * WebKit on macOS only lists them all, so its backend filters the list here. The rules are those of
 * the domain, the path, {@code Secure}, and expiry; {@code SameSite} doesn't apply, since there is
 * no site that the request comes from.
 */
@UtilityClass
public class CookieUtil {
  /** Whether a request to {@code uri} at {@code now} carries {@code cookie}. */
  public boolean isSentTo(Cookie cookie, URI uri, Instant now) {
    String host = uri.getHost();
    if (host == null) {
      return false;
    }
    if (cookie.secure() && !"https".equalsIgnoreCase(uri.getScheme())) {
      return false;
    }
    if (cookie.expires() != null && !cookie.expires().isAfter(now)) {
      return false;
    }
    return CookieUtil.domainMatches(cookie.domain(), host)
        && CookieUtil.pathMatches(cookie.path(), uri.getRawPath());
  }

  /**
   * Whether {@code host} is the host of a cookie of {@code domain}: the host itself, or, for a
   * domain that starts with a dot, the domain or a subdomain of it.
   */
  private boolean domainMatches(String domain, String host) {
    String wanted = domain.toLowerCase(Locale.ROOT);
    String actual = host.toLowerCase(Locale.ROOT);
    if (!wanted.startsWith(".")) {
      return actual.equals(wanted);
    }
    return actual.equals(wanted.substring(1)) || actual.endsWith(wanted);
  }

  /** Whether {@code requestPath} is at or under the path of the cookie, in whole segments. */
  private boolean pathMatches(String cookiePath, String requestPath) {
    String path = requestPath == null || requestPath.isEmpty() ? "/" : requestPath;
    if (path.equals(cookiePath)) {
      return true;
    }
    return path.startsWith(cookiePath)
        && (cookiePath.endsWith("/") || path.charAt(cookiePath.length()) == '/');
  }
}
