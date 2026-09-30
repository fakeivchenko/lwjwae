package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.cookie.Cookie;
import dev.ivchenko.lwjwae.cookie.SameSite;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Bindings to {@code ICoreWebView2CookieManager}, {@code ICoreWebView2Cookie}, and {@code
 * ICoreWebView2CookieList}, which WebView2 runtime 89 and later have.
 *
 * <p>A view reaches the manager of its profile through {@code ICoreWebView2_2}, so every view of
 * the application reaches the same cookies. A cookie of WebView2 is an object that the manager
 * creates and that the application then fills, so {@link #create} turns a {@link Cookie} into one
 * and {@link #read} turns one back.
 */
@UtilityClass
public class CookieManager {
  /** {@code IID_ICoreWebView2GetCookiesCompletedHandler}. */
  public final MemorySegment IID_GET_COOKIES_COMPLETED =
      Com.guid("5a4f5069-5c15-47c3-8646-f4de1c116670");

  private final MemorySegment IID_WEBVIEW_2 = Com.guid("9e8f0cf8-e670-4b5e-b2bc-73e061e3184c");

  // ICoreWebView2_2
  private final int WEBVIEW_2_GET_COOKIE_MANAGER = 66;

  // ICoreWebView2CookieManager
  private final int MANAGER_CREATE_COOKIE = 3;
  private final int MANAGER_GET_COOKIES = 5;
  private final int MANAGER_ADD_OR_UPDATE_COOKIE = 6;
  private final int MANAGER_DELETE_COOKIES_WITH_DOMAIN_AND_PATH = 9;
  private final int MANAGER_DELETE_ALL_COOKIES = 10;

  // ICoreWebView2Cookie
  private final int COOKIE_GET_NAME = 3;
  private final int COOKIE_GET_VALUE = 4;
  private final int COOKIE_GET_DOMAIN = 6;
  private final int COOKIE_GET_PATH = 7;
  private final int COOKIE_GET_EXPIRES = 8;
  private final int COOKIE_PUT_EXPIRES = 9;
  private final int COOKIE_GET_IS_HTTP_ONLY = 10;
  private final int COOKIE_PUT_IS_HTTP_ONLY = 11;
  private final int COOKIE_GET_SAME_SITE = 12;
  private final int COOKIE_PUT_SAME_SITE = 13;
  private final int COOKIE_GET_IS_SECURE = 14;
  private final int COOKIE_PUT_IS_SECURE = 15;
  private final int COOKIE_GET_IS_SESSION = 16;

  // ICoreWebView2CookieList
  private final int LIST_GET_COUNT = 3;
  private final int LIST_GET_VALUE_AT_INDEX = 4;

  /** {@code COREWEBVIEW2_COOKIE_SAME_SITE_KIND_NONE}, {@code _LAX}, {@code _STRICT}. */
  private final int SAME_SITE_NONE = 0;

  private final int SAME_SITE_LAX = 1;
  private final int SAME_SITE_STRICT = 2;

  /** The cookie manager of the profile of {@code webView}. The caller owns the reference. */
  public MemorySegment of(MemorySegment webView) {
    MemorySegment webView2 = WinRt.query(webView, IID_WEBVIEW_2);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_CookieManager", Com.call(webView2, WEBVIEW_2_GET_COOKIE_MANAGER, out));
      return Com.pointerAt(out);
    } finally {
      Com.release(webView2);
    }
  }

  /**
   * Calls {@code GetCookies}: the handler gets the cookies of {@code uri}, or of every site for an
   * empty one, as an {@code ICoreWebView2CookieList}.
   */
  public void getCookies(MemorySegment manager, String uri, MemorySegment handler) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check(
          "GetCookies", Com.call(manager, MANAGER_GET_COOKIES, Wide.allocate(arena, uri), handler));
    }
  }

  /** Sets {@code cookie}, in place of the one of its name, domain, and path. */
  public void addOrUpdate(MemorySegment manager, Cookie cookie) {
    MemorySegment created = CookieManager.create(manager, cookie);
    try {
      Com.check("AddOrUpdateCookie", Com.call(manager, MANAGER_ADD_OR_UPDATE_COOKIE, created));
    } finally {
      Com.release(created);
    }
  }

  /** Deletes the cookies of the name, the domain, and the path of {@code cookie}. */
  public void delete(MemorySegment manager, Cookie cookie) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check(
          "DeleteCookiesWithDomainAndPath",
          Com.call(
              manager,
              MANAGER_DELETE_COOKIES_WITH_DOMAIN_AND_PATH,
              Wide.allocate(arena, cookie.name()),
              Wide.allocate(arena, cookie.domain()),
              Wide.allocate(arena, cookie.path())));
    }
  }

  /** Deletes every cookie of the profile. */
  public void deleteAll(MemorySegment manager) {
    Com.check("DeleteAllCookies", Com.call(manager, MANAGER_DELETE_ALL_COOKIES));
  }

  /** The cookies of an {@code ICoreWebView2CookieList}, which stays the caller's. */
  public List<Cookie> read(MemorySegment list) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_Count", Com.call(list, LIST_GET_COUNT, out));
      int count = out.get(Signatures.C_INT, 0);
      List<Cookie> cookies = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
        Com.check("GetValueAtIndex", Com.call(list, LIST_GET_VALUE_AT_INDEX, index, out));
        MemorySegment cookie = Com.pointerAt(out);
        try {
          cookies.add(CookieManager.cookie(arena, cookie));
        } finally {
          Com.release(cookie);
        }
      }
      return cookies;
    }
  }

  /** A new {@code ICoreWebView2Cookie} that says what {@code cookie} does. The caller owns it. */
  private MemorySegment create(MemorySegment manager, Cookie cookie) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "CreateCookie",
          Com.call(
              manager,
              MANAGER_CREATE_COOKIE,
              Wide.allocate(arena, cookie.name()),
              Wide.allocate(arena, cookie.value()),
              Wide.allocate(arena, cookie.domain()),
              Wide.allocate(arena, cookie.path()),
              out));
      MemorySegment created = Com.pointerAt(out);
      if (cookie.expires() != null) {
        Com.check(
            "put_Expires",
            Com.call(created, COOKIE_PUT_EXPIRES, (double) cookie.expires().toEpochMilli() / 1000));
      }
      Com.check(
          "put_IsHttpOnly", Com.call(created, COOKIE_PUT_IS_HTTP_ONLY, cookie.httpOnly() ? 1 : 0));
      Com.check("put_IsSecure", Com.call(created, COOKIE_PUT_IS_SECURE, cookie.secure() ? 1 : 0));
      if (cookie.sameSite() != null) {
        int kind =
            switch (cookie.sameSite()) {
              case NONE -> SAME_SITE_NONE;
              case LAX -> SAME_SITE_LAX;
              case STRICT -> SAME_SITE_STRICT;
            };
        Com.check("put_SameSite", Com.call(created, COOKIE_PUT_SAME_SITE, kind));
      }
      return created;
    }
  }

  private Cookie cookie(Arena arena, MemorySegment cookie) {
    MemorySegment expires = arena.allocate(Signatures.C_DOUBLE);
    Com.check("get_Expires", Com.call(cookie, COOKIE_GET_EXPIRES, expires));
    double seconds = expires.get(Signatures.C_DOUBLE, 0);
    return Cookie.builder()
        .name(CookieManager.string(arena, cookie, COOKIE_GET_NAME, "get_Name"))
        .value(CookieManager.string(arena, cookie, COOKIE_GET_VALUE, "get_Value"))
        .domain(CookieManager.string(arena, cookie, COOKIE_GET_DOMAIN, "get_Domain"))
        .path(CookieManager.string(arena, cookie, COOKIE_GET_PATH, "get_Path"))
        .expires(
            CookieManager.integer(arena, cookie, COOKIE_GET_IS_SESSION, "get_IsSession") != 0
                ? null
                : Instant.ofEpochMilli(Math.round(seconds * 1000)))
        .httpOnly(
            CookieManager.integer(arena, cookie, COOKIE_GET_IS_HTTP_ONLY, "get_IsHttpOnly") != 0)
        .secure(CookieManager.integer(arena, cookie, COOKIE_GET_IS_SECURE, "get_IsSecure") != 0)
        .sameSite(
            switch (CookieManager.integer(arena, cookie, COOKIE_GET_SAME_SITE, "get_SameSite")) {
              case SAME_SITE_LAX -> SameSite.LAX;
              case SAME_SITE_STRICT -> SameSite.STRICT;
              default -> SameSite.NONE;
            })
        .build();
  }

  private String string(Arena arena, MemorySegment object, int index, String call) {
    MemorySegment out = arena.allocate(Signatures.C_POINTER);
    Com.check(call, Com.call(object, index, out));
    return Wide.take(Com.pointerAt(out));
  }

  private int integer(Arena arena, MemorySegment object, int index, String call) {
    MemorySegment out = arena.allocate(Signatures.C_INT);
    Com.check(call, Com.call(object, index, out));
    return out.get(Signatures.C_INT, 0);
  }
}
