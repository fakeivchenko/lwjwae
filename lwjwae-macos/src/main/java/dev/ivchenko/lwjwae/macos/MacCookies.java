package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.cookie.Cookie;
import dev.ivchenko.lwjwae.cookie.Cookies;
import dev.ivchenko.lwjwae.cookie.SameSite;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.macos.binding.Signatures;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.CookieUtil;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * The cookies of WebKit on macOS, through the {@code WKHTTPCookieStore} of the default data store,
 * which every web view of the application uses.
 *
 * <p>The store answers in blocks on the main thread: each call makes a block whose context is the
 * ID of its future, and the upcall settles the future. The store only lists every cookie, so {@link
 * #get} filters the list by the rules of the browser, and {@link #clear} deletes what it lists.
 *
 * <p>A cookie goes in and out as an {@code NSHTTPCookie}, whose properties are a dictionary with
 * the keys that Foundation exports. {@code HttpOnly} has no exported key, so it's the one that
 * {@code NSHTTPCookie} reads from a {@code Set-Cookie} header.
 */
final class MacCookies implements Cookies {
  private static final CallbackRegistry<PendingCompletion<List<Cookie>>> PENDING_LISTS =
      new CallbackRegistry<>();
  private static final CallbackRegistry<PendingCompletion<Void>> PENDING_CHANGES =
      new CallbackRegistry<>();

  private static final MemorySegment ON_LISTED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacCookies.class,
          "onListed",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.RESULT_BLOCK);
  private static final MemorySegment ON_CHANGED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacCookies.class,
          "onChanged",
          MethodType.methodType(void.class, MemorySegment.class),
          Signatures.DONE_BLOCK);

  private static final SymbolLookup FOUNDATION =
      NativeLibraries.load("/System/Library/Frameworks/Foundation.framework/Foundation");
  private static final MemorySegment NAME = MacCookies.constant("NSHTTPCookieName");
  private static final MemorySegment VALUE = MacCookies.constant("NSHTTPCookieValue");
  private static final MemorySegment DOMAIN = MacCookies.constant("NSHTTPCookieDomain");
  private static final MemorySegment PATH = MacCookies.constant("NSHTTPCookiePath");
  private static final MemorySegment EXPIRES = MacCookies.constant("NSHTTPCookieExpires");
  private static final MemorySegment SECURE = MacCookies.constant("NSHTTPCookieSecure");
  private static final MemorySegment SAME_SITE_POLICY =
      MacCookies.constant("NSHTTPCookieSameSitePolicy");
  private static final MemorySegment SAME_SITE_LAX = MacCookies.constant("NSHTTPCookieSameSiteLax");
  private static final MemorySegment SAME_SITE_STRICT =
      MacCookies.constant("NSHTTPCookieSameSiteStrict");
  private static final String HTTP_ONLY = "HttpOnly";

  private final UiDispatcher dispatcher;

  MacCookies(UiDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public CompletableFuture<List<Cookie>> get(String url) {
    URI uri = URI.create(url);
    if (!uri.isAbsolute()) {
      throw new IllegalArgumentException("Not an absolute URL: " + url);
    }
    Instant now = Instant.now();
    return this.getAll()
        .thenApply(
            cookies ->
                cookies.stream().filter(cookie -> CookieUtil.isSentTo(cookie, uri, now)).toList());
  }

  @Override
  public CompletableFuture<List<Cookie>> getAll() {
    return this.start(
        PENDING_LISTS, ON_LISTED, (store, block) -> ObjC.sendVoid(store, "getAllCookies:", block));
  }

  @Override
  public CompletableFuture<Void> set(Cookie cookie) {
    Objects.requireNonNull(cookie, "cookie");
    return this.start(
        PENDING_CHANGES,
        ON_CHANGED,
        (store, block) ->
            ObjC.sendVoid(
                store, "setCookie:completionHandler:", MacCookies.httpCookie(cookie), block));
  }

  @Override
  public CompletableFuture<Void> delete(Cookie cookie) {
    Objects.requireNonNull(cookie, "cookie");
    return this.start(
        PENDING_CHANGES,
        ON_CHANGED,
        (store, block) ->
            ObjC.sendVoid(
                store, "deleteCookie:completionHandler:", MacCookies.httpCookie(cookie), block));
  }

  @Override
  public CompletableFuture<Void> clear() {
    return this.getAll()
        .thenCompose(
            cookies ->
                CompletableFuture.allOf(
                    cookies.stream().map(this::delete).toArray(CompletableFuture[]::new)));
  }

  /**
   * Sends a message to the cookie store on the main thread, with a block that settles the future
   * that {@code pending} keeps under the ID in its context.
   */
  private <T> CompletableFuture<T> start(
      CallbackRegistry<PendingCompletion<T>> pending,
      MemorySegment invoke,
      BiConsumer<MemorySegment, MemorySegment> send) {
    CompletableFuture<T> result = new CompletableFuture<>();
    Arena arena = Arena.ofAuto();
    long id = pending.register(new PendingCompletion<>(result, arena));
    this.dispatcher.post(
        () -> {
          try {
            MemorySegment store =
                ObjC.send(
                    ObjC.send(ObjC.cls("WKWebsiteDataStore"), "defaultDataStore"),
                    "httpCookieStore");
            send.accept(store, ObjC.block(arena, invoke, id));
          } catch (Throwable t) {
            pending.unregister(id);
            result.completeExceptionally(t);
          }
        });
    return result;
  }

  /** An autoreleased {@code NSHTTPCookie} that says what {@code cookie} does. */
  private static MemorySegment httpCookie(Cookie cookie) {
    MemorySegment properties = ObjC.send(ObjC.cls("NSMutableDictionary"), "dictionary");
    MacCookies.put(properties, NAME, Foundation.string(cookie.name()));
    MacCookies.put(properties, VALUE, Foundation.string(cookie.value()));
    MacCookies.put(properties, DOMAIN, Foundation.string(cookie.domain()));
    MacCookies.put(properties, PATH, Foundation.string(cookie.path()));
    if (cookie.expires() != null) {
      MacCookies.put(
          properties,
          EXPIRES,
          ObjC.send(
              ObjC.cls("NSDate"),
              "dateWithTimeIntervalSince1970:",
              cookie.expires().toEpochMilli() / 1000.0));
    }
    if (cookie.secure()) {
      MacCookies.put(properties, SECURE, Foundation.string("TRUE"));
    }
    if (cookie.httpOnly()) {
      MacCookies.put(properties, Foundation.string(HTTP_ONLY), Foundation.string("TRUE"));
    }
    if (cookie.sameSite() == SameSite.LAX) {
      MacCookies.put(properties, SAME_SITE_POLICY, SAME_SITE_LAX);
    } else if (cookie.sameSite() == SameSite.STRICT) {
      MacCookies.put(properties, SAME_SITE_POLICY, SAME_SITE_STRICT);
    }
    MemorySegment created =
        ObjC.send(ObjC.cls("NSHTTPCookie"), "cookieWithProperties:", properties);
    if (ObjC.isNull(created)) {
      throw new IllegalArgumentException("WebKit takes no such cookie: " + cookie);
    }
    return created;
  }

  private static Cookie cookie(MemorySegment httpCookie) {
    MemorySegment expires = ObjC.send(httpCookie, "expiresDate");
    MemorySegment sameSite = ObjC.send(httpCookie, "sameSitePolicy");
    return Cookie.builder()
        .name(Foundation.string(ObjC.send(httpCookie, "name")))
        .value(Foundation.string(ObjC.send(httpCookie, "value")))
        .domain(Foundation.string(ObjC.send(httpCookie, "domain")))
        .path(Foundation.string(ObjC.send(httpCookie, "path")))
        .expires(
            ObjC.isNull(expires)
                ? null
                : Instant.ofEpochMilli(
                    Math.round(Foundation.doubleValue(expires, "timeIntervalSince1970") * 1000)))
        .secure(ObjC.sendBool(httpCookie, "isSecure"))
        .httpOnly(ObjC.sendBool(httpCookie, "isHTTPOnly"))
        .sameSite(MacCookies.sameSite(sameSite))
        .build();
  }

  private static SameSite sameSite(MemorySegment policy) {
    if (ObjC.isNull(policy)) {
      return SameSite.NONE;
    }
    if (ObjC.sendBool(policy, "isEqualToString:", SAME_SITE_LAX)) {
      return SameSite.LAX;
    }
    if (ObjC.sendBool(policy, "isEqualToString:", SAME_SITE_STRICT)) {
      return SameSite.STRICT;
    }
    return SameSite.NONE;
  }

  private static void put(MemorySegment dictionary, MemorySegment key, MemorySegment value) {
    ObjC.sendVoid(dictionary, "setObject:forKey:", value, key);
  }

  /** The {@code NSString} that the exported constant {@code name} of Foundation holds. */
  private static MemorySegment constant(String name) {
    return FOUNDATION
        .findOrThrow(name)
        .reinterpret(Signatures.C_POINTER.byteSize())
        .get(Signatures.C_POINTER, 0);
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onListed(MemorySegment block, MemorySegment cookies) {
    PendingCompletion<List<Cookie>> pending = PENDING_LISTS.unregister(ObjC.blockContext(block));
    if (pending == null) {
      return;
    }
    try {
      long count = ObjC.sendLong(cookies, "count");
      List<Cookie> listed = new ArrayList<>((int) count);
      for (long index = 0; index < count; index++) {
        listed.add(MacCookies.cookie(ObjC.send(cookies, "objectAtIndex:", index)));
      }
      pending.result().complete(listed);
    } catch (Throwable t) {
      pending.result().completeExceptionally(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onChanged(MemorySegment block) {
    PendingCompletion<Void> pending = PENDING_CHANGES.unregister(ObjC.blockContext(block));
    if (pending != null) {
      pending.result().complete(null);
    }
  }
}
