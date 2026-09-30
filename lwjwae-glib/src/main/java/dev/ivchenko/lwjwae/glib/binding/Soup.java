package dev.ivchenko.lwjwae.glib.binding;

import dev.ivchenko.lwjwae.cookie.Cookie;
import dev.ivchenko.lwjwae.cookie.SameSite;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the {@code SoupCookie} of libsoup 3, which WebKitGTK 4.1 and 6.0 both take and give,
 * and to the {@code GDateTime} of its expiry.
 */
@UtilityClass
public class Soup {
  private final SymbolLookup SOUP = NativeLibraries.load("libsoup-3.0.so.0", "libsoup-3.0.so");
  private final SymbolLookup GLIB = NativeLibraries.load("libglib-2.0.so.0", "libglib-2.0.so");

  /** {@code SOUP_SAME_SITE_POLICY_NONE}, {@code _LAX}, {@code _STRICT}. */
  private final int SAME_SITE_NONE = 0;

  private final int SAME_SITE_LAX = 1;
  private final int SAME_SITE_STRICT = 2;

  private final MethodHandle COOKIE_NEW =
      NativeLibraries.downcall(SOUP, "soup_cookie_new", Signatures.POINTER_POINTER_X4_INT);
  private final MethodHandle COOKIE_GET_NAME =
      NativeLibraries.downcall(SOUP, "soup_cookie_get_name", Signatures.POINTER_POINTER);
  private final MethodHandle COOKIE_GET_VALUE =
      NativeLibraries.downcall(SOUP, "soup_cookie_get_value", Signatures.POINTER_POINTER);
  private final MethodHandle COOKIE_GET_DOMAIN =
      NativeLibraries.downcall(SOUP, "soup_cookie_get_domain", Signatures.POINTER_POINTER);
  private final MethodHandle COOKIE_GET_PATH =
      NativeLibraries.downcall(SOUP, "soup_cookie_get_path", Signatures.POINTER_POINTER);
  private final MethodHandle COOKIE_GET_EXPIRES =
      NativeLibraries.downcall(SOUP, "soup_cookie_get_expires", Signatures.POINTER_POINTER);
  private final MethodHandle COOKIE_GET_SECURE =
      NativeLibraries.downcall(SOUP, "soup_cookie_get_secure", Signatures.INT_POINTER);
  private final MethodHandle COOKIE_GET_HTTP_ONLY =
      NativeLibraries.downcall(SOUP, "soup_cookie_get_http_only", Signatures.INT_POINTER);
  private final MethodHandle COOKIE_GET_SAME_SITE_POLICY =
      NativeLibraries.downcall(SOUP, "soup_cookie_get_same_site_policy", Signatures.INT_POINTER);
  private final MethodHandle COOKIE_SET_EXPIRES =
      NativeLibraries.downcall(SOUP, "soup_cookie_set_expires", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle COOKIE_SET_SECURE =
      NativeLibraries.downcall(SOUP, "soup_cookie_set_secure", Signatures.VOID_POINTER_INT);
  private final MethodHandle COOKIE_SET_HTTP_ONLY =
      NativeLibraries.downcall(SOUP, "soup_cookie_set_http_only", Signatures.VOID_POINTER_INT);
  private final MethodHandle COOKIE_SET_SAME_SITE_POLICY =
      NativeLibraries.downcall(
          SOUP, "soup_cookie_set_same_site_policy", Signatures.VOID_POINTER_INT);
  private final MethodHandle COOKIE_FREE =
      NativeLibraries.downcall(SOUP, "soup_cookie_free", Signatures.VOID_POINTER);
  private final MethodHandle DATE_TIME_NEW_FROM_UNIX_UTC =
      NativeLibraries.downcall(GLIB, "g_date_time_new_from_unix_utc", Signatures.POINTER_LONG);
  private final MethodHandle DATE_TIME_TO_UNIX =
      NativeLibraries.downcall(GLIB, "g_date_time_to_unix", Signatures.LONG_POINTER);
  private final MethodHandle DATE_TIME_UNREF =
      NativeLibraries.downcall(GLIB, "g_date_time_unref", Signatures.VOID_POINTER);
  private final MethodHandle LIST_FREE =
      NativeLibraries.downcall(GLIB, "g_list_free", Signatures.VOID_POINTER);

  /** A new {@code SoupCookie} of {@code cookie}, which the caller frees with {@link #free}. */
  @SneakyThrows
  public MemorySegment cookie(Cookie cookie) {
    MemorySegment created;
    try (Arena arena = Arena.ofConfined()) {
      created =
          (MemorySegment)
              COOKIE_NEW.invokeExact(
                  arena.allocateFrom(cookie.name()),
                  arena.allocateFrom(cookie.value()),
                  arena.allocateFrom(cookie.domain()),
                  arena.allocateFrom(cookie.path()),
                  -1);
    }
    if (cookie.expires() != null) {
      MemorySegment date =
          (MemorySegment)
              DATE_TIME_NEW_FROM_UNIX_UTC.invokeExact(cookie.expires().getEpochSecond());
      COOKIE_SET_EXPIRES.invokeExact(created, date);
      DATE_TIME_UNREF.invokeExact(date);
    }
    COOKIE_SET_SECURE.invokeExact(created, cookie.secure() ? 1 : 0);
    COOKIE_SET_HTTP_ONLY.invokeExact(created, cookie.httpOnly() ? 1 : 0);
    if (cookie.sameSite() != null) {
      int policy =
          switch (cookie.sameSite()) {
            case NONE -> SAME_SITE_NONE;
            case LAX -> SAME_SITE_LAX;
            case STRICT -> SAME_SITE_STRICT;
          };
      COOKIE_SET_SAME_SITE_POLICY.invokeExact(created, policy);
    }
    return created;
  }

  /** The cookie that {@code soupCookie} describes. */
  @SneakyThrows
  public Cookie read(MemorySegment soupCookie) {
    MemorySegment expires = (MemorySegment) COOKIE_GET_EXPIRES.invokeExact(soupCookie);
    int policy = (int) COOKIE_GET_SAME_SITE_POLICY.invokeExact(soupCookie);
    return Cookie.builder()
        .name(NativeLibraries.string((MemorySegment) COOKIE_GET_NAME.invokeExact(soupCookie)))
        .value(NativeLibraries.string((MemorySegment) COOKIE_GET_VALUE.invokeExact(soupCookie)))
        .domain(NativeLibraries.string((MemorySegment) COOKIE_GET_DOMAIN.invokeExact(soupCookie)))
        .path(NativeLibraries.string((MemorySegment) COOKIE_GET_PATH.invokeExact(soupCookie)))
        .expires(
            expires.equals(MemorySegment.NULL)
                ? null
                : Instant.ofEpochSecond((long) DATE_TIME_TO_UNIX.invokeExact(expires)))
        .secure((int) COOKIE_GET_SECURE.invokeExact(soupCookie) != 0)
        .httpOnly((int) COOKIE_GET_HTTP_ONLY.invokeExact(soupCookie) != 0)
        .sameSite(
            switch (policy) {
              case SAME_SITE_NONE -> SameSite.NONE;
              case SAME_SITE_LAX -> SameSite.LAX;
              case SAME_SITE_STRICT -> SameSite.STRICT;
              default -> null;
            })
        .build();
  }

  /** Frees a {@code SoupCookie}. */
  @SneakyThrows
  public void free(MemorySegment soupCookie) {
    COOKIE_FREE.invokeExact(soupCookie);
  }

  /**
   * The cookies of a {@code GList} of {@code SoupCookie} that the caller owns, which this frees
   * with its cookies.
   */
  @SneakyThrows
  public List<Cookie> takeList(MemorySegment list) {
    List<Cookie> cookies = new ArrayList<>();
    MemorySegment node = list;
    while (!node.equals(MemorySegment.NULL)) {
      // GList: data, next, prev.
      MemorySegment element = node.reinterpret(3 * Signatures.C_POINTER.byteSize());
      MemorySegment cookie = element.get(Signatures.C_POINTER, 0);
      cookies.add(Soup.read(cookie));
      Soup.free(cookie);
      node = element.get(Signatures.C_POINTER, Signatures.C_POINTER.byteSize());
    }
    LIST_FREE.invokeExact(list);
    return cookies;
  }
}
