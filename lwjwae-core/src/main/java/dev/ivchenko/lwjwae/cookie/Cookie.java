package dev.ivchenko.lwjwae.cookie;

import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/**
 * A cookie of the engine: what a site set, or what the application sets for a site.
 *
 * <pre>{@code
 * application.cookies().set(
 *     Cookie.builder().name("session").value(token).domain("example.com").secure(true).build());
 * }</pre>
 *
 * @param name The name. Required.
 * @param value The value. Default: empty.
 * @param domain The host that the cookie belongs to, such as {@code example.com}; one that starts
 *     with a dot, such as {@code .example.com}, belongs to its subdomains too. Required.
 * @param path The path under which the browser sends it. Default: {@code /}.
 * @param expires When the cookie goes, or {@code null} for a cookie of the session, which goes when
 *     the application ends.
 * @param secure Whether the browser sends it over HTTPS only.
 * @param httpOnly Whether the page can't read it through {@code document.cookie}.
 * @param sameSite When the browser sends it with a request of another site, or {@code null} for the
 *     default of the engine.
 */
@Builder(toBuilder = true)
public record Cookie(
    String name,
    String value,
    String domain,
    String path,
    Instant expires,
    boolean secure,
    boolean httpOnly,
    SameSite sameSite) {
  public Cookie {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(domain, "domain");
    if (value == null) {
      value = "";
    }
    if (path == null || path.isEmpty()) {
      path = "/";
    }
  }

  /** Whether the cookie goes when the application ends. */
  public boolean isSession() {
    return this.expires == null;
  }
}
