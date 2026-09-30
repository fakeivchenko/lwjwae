package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.cookie.Cookie;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CookieUtilTest {
  private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

  @Test
  void hostOnlyCookieGoesToItsHostAlone() {
    Cookie cookie = Cookie.builder().name("a").domain("example.com").build();
    Assertions.assertTrue(CookieUtilTest.sent(cookie, "http://example.com/"));
    Assertions.assertTrue(CookieUtilTest.sent(cookie, "http://EXAMPLE.com/page"));
    Assertions.assertFalse(CookieUtilTest.sent(cookie, "http://www.example.com/"));
    Assertions.assertFalse(CookieUtilTest.sent(cookie, "http://badexample.com/"));
  }

  @Test
  void domainCookieGoesToSubdomainsToo() {
    Cookie cookie = Cookie.builder().name("a").domain(".example.com").build();
    Assertions.assertTrue(CookieUtilTest.sent(cookie, "http://example.com/"));
    Assertions.assertTrue(CookieUtilTest.sent(cookie, "http://www.example.com/"));
    Assertions.assertFalse(CookieUtilTest.sent(cookie, "http://badexample.com/"));
  }

  @Test
  void pathMatchesInWholeSegments() {
    Cookie cookie = Cookie.builder().name("a").domain("example.com").path("/docs").build();
    Assertions.assertTrue(CookieUtilTest.sent(cookie, "http://example.com/docs"));
    Assertions.assertTrue(CookieUtilTest.sent(cookie, "http://example.com/docs/intro"));
    Assertions.assertFalse(CookieUtilTest.sent(cookie, "http://example.com/docsets"));
    Assertions.assertFalse(CookieUtilTest.sent(cookie, "http://example.com/"));
    Assertions.assertTrue(
        CookieUtilTest.sent(
            cookie.toBuilder().path("/docs/").build(), "http://example.com/docs/a"));
  }

  @Test
  void secureCookieGoesOverHttpsAlone() {
    Cookie cookie = Cookie.builder().name("a").domain("example.com").secure(true).build();
    Assertions.assertTrue(CookieUtilTest.sent(cookie, "https://example.com/"));
    Assertions.assertFalse(CookieUtilTest.sent(cookie, "http://example.com/"));
  }

  @Test
  void expiredCookieGoesNowhere() {
    Cookie cookie = Cookie.builder().name("a").domain("example.com").build();
    Assertions.assertFalse(
        CookieUtilTest.sent(
            cookie.toBuilder().expires(NOW.minusSeconds(1)).build(), "http://example.com/"));
    Assertions.assertTrue(
        CookieUtilTest.sent(
            cookie.toBuilder().expires(NOW.plusSeconds(1)).build(), "http://example.com/"));
  }

  private static boolean sent(Cookie cookie, String url) {
    return CookieUtil.isSentTo(cookie, URI.create(url), NOW);
  }
}
