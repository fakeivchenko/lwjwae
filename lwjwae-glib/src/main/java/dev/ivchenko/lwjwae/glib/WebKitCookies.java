package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.cookie.Cookie;
import dev.ivchenko.lwjwae.cookie.Cookies;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.Signatures;
import dev.ivchenko.lwjwae.glib.binding.Soup;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.SneakyThrows;

/**
 * The cookies of WebKitGTK, through its {@code WebKitCookieManager}, the same on GTK 3 and GTK 4.
 *
 * <p>The two libraries of WebKitGTK have the manager under the same names, but a process may load
 * only the one of its toolkit, so the backend names it, and the functions are bound here from it.
 * Every call starts on the GTK thread and finishes in a {@code GAsyncReadyCallback} there, which
 * finds the future of the call by the ID that it carries as its data.
 */
public final class WebKitCookies implements Cookies {
  private static final CallbackRegistry<Consumer<MemorySegment>> PENDING = new CallbackRegistry<>();
  private static final MemorySegment ON_READY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitCookies.class,
          "onReady",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.G_ASYNC_READY_CALLBACK);

  private final UiDispatcher dispatcher;
  private final MemorySegment manager;
  private final MethodHandle getCookies;
  private final MethodHandle getCookiesFinish;
  private final MethodHandle getAllCookies;
  private final MethodHandle getAllCookiesFinish;
  private final MethodHandle addCookie;
  private final MethodHandle addCookieFinish;
  private final MethodHandle deleteCookie;
  private final MethodHandle deleteCookieFinish;

  /**
   * Binds the cookie manager of the library of WebKitGTK that the process loaded.
   *
   * @param webkit The name of the library of WebKitGTK that the process loaded, such as {@code
   *     libwebkit2gtk-4.1.so.0}.
   * @param manager The {@code WebKitCookieManager}, which lives as long as its context.
   */
  public WebKitCookies(UiDispatcher dispatcher, String webkit, MemorySegment manager) {
    this.dispatcher = dispatcher;
    this.manager = manager;
    SymbolLookup library = NativeLibraries.load(webkit);
    this.getCookies =
        NativeLibraries.downcall(
            library, "webkit_cookie_manager_get_cookies", Signatures.VOID_POINTER_X5);
    this.getCookiesFinish =
        NativeLibraries.downcall(
            library,
            "webkit_cookie_manager_get_cookies_finish",
            Signatures.POINTER_POINTER_POINTER_POINTER);
    // From WebKitGTK 2.42 on.
    boolean listsAll = library.find("webkit_cookie_manager_get_all_cookies").isPresent();
    this.getAllCookies =
        NativeLibraries.downcallIfPresent(
            listsAll ? library : null,
            "webkit_cookie_manager_get_all_cookies",
            Signatures.VOID_POINTER_X4);
    this.getAllCookiesFinish =
        NativeLibraries.downcallIfPresent(
            listsAll ? library : null,
            "webkit_cookie_manager_get_all_cookies_finish",
            Signatures.POINTER_POINTER_POINTER_POINTER);
    this.addCookie =
        NativeLibraries.downcall(
            library, "webkit_cookie_manager_add_cookie", Signatures.VOID_POINTER_X5);
    this.addCookieFinish =
        NativeLibraries.downcall(
            library,
            "webkit_cookie_manager_add_cookie_finish",
            Signatures.INT_POINTER_POINTER_POINTER);
    this.deleteCookie =
        NativeLibraries.downcall(
            library, "webkit_cookie_manager_delete_cookie", Signatures.VOID_POINTER_X5);
    this.deleteCookieFinish =
        NativeLibraries.downcall(
            library,
            "webkit_cookie_manager_delete_cookie_finish",
            Signatures.INT_POINTER_POINTER_POINTER);
  }

  @Override
  public CompletableFuture<List<Cookie>> get(String url) {
    URI uri = URI.create(url);
    if (!uri.isAbsolute()) {
      throw new IllegalArgumentException("Not an absolute URL: " + url);
    }
    return this.start(
        (arena, data) -> {
          this.getCookies.invokeExact(
              this.manager, arena.allocateFrom(url), MemorySegment.NULL, ON_READY, data);
        },
        result -> Soup.takeList(this.finishPointer(this.getCookiesFinish, result)));
  }

  /**
   * Every cookie: {@code webkit_cookie_manager_get_all_cookies}, which WebKitGTK has from 2.42 on.
   */
  @Override
  public CompletableFuture<List<Cookie>> getAll() {
    if (this.getAllCookies == null) {
      return CompletableFuture.failedFuture(
          new UnsupportedOperationException("WebKitGTK older than 2.42 lists no cookies"));
    }
    return this.start(
        (_, data) -> {
          this.getAllCookies.invokeExact(this.manager, MemorySegment.NULL, ON_READY, data);
        },
        result -> Soup.takeList(this.finishPointer(this.getAllCookiesFinish, result)));
  }

  @Override
  public CompletableFuture<Void> set(Cookie cookie) {
    return this.change(this.addCookie, this.addCookieFinish, cookie);
  }

  @Override
  public CompletableFuture<Void> delete(Cookie cookie) {
    return this.change(this.deleteCookie, this.deleteCookieFinish, cookie);
  }

  @Override
  public CompletableFuture<Void> clear() {
    return this.getAll()
        .thenCompose(
            cookies -> {
              List<CompletableFuture<Void>> deletions = new ArrayList<>();
              cookies.forEach(cookie -> deletions.add(this.delete(cookie)));
              return CompletableFuture.allOf(deletions.toArray(CompletableFuture[]::new));
            });
  }

  /** Adds or deletes {@code cookie}, as a {@code SoupCookie} that lives until the call returns. */
  private CompletableFuture<Void> change(
      MethodHandle operation, MethodHandle finish, Cookie cookie) {
    MemorySegment[] soupCookie = {MemorySegment.NULL};
    return this.start(
        (_, data) -> {
          soupCookie[0] = Soup.cookie(cookie);
          operation.invokeExact(this.manager, soupCookie[0], MemorySegment.NULL, ON_READY, data);
        },
        result -> {
          Soup.free(soupCookie[0]);
          this.finishBoolean(finish, result);
          return null;
        });
  }

  /**
   * Starts a call on the GTK thread and completes its future with what {@code finish} makes of the
   * {@code GAsyncResult}, on the GTK thread too.
   */
  private <T> CompletableFuture<T> start(AsyncCall start, AsyncResultReader<T> finish) {
    CompletableFuture<T> future = new CompletableFuture<>();
    long id =
        PENDING.register(
            result -> {
              try {
                future.complete(finish.read(result));
              } catch (Throwable t) {
                future.completeExceptionally(t);
              }
            });
    this.dispatcher.post(
        () -> {
          try (Arena arena = Arena.ofConfined()) {
            start.start(arena, CallbackRegistry.userData(id));
          } catch (Throwable t) {
            PENDING.unregister(id);
            future.completeExceptionally(t);
          }
        });
    return future;
  }

  @SneakyThrows
  private MemorySegment finishPointer(MethodHandle finish, MemorySegment result) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      MemorySegment value = (MemorySegment) finish.invokeExact(this.manager, result, error);
      String message = Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0));
      if (message != null) {
        throw new IllegalStateException(message);
      }
      return value;
    }
  }

  @SneakyThrows
  private void finishBoolean(MethodHandle finish, MemorySegment result) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment error = arena.allocate(Signatures.C_POINTER);
      int _ = (int) finish.invokeExact(this.manager, result, error);
      String message = Glib.takeErrorMessage(error.get(Signatures.C_POINTER, 0));
      if (message != null) {
        throw new IllegalStateException(message);
      }
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onReady(MemorySegment source, MemorySegment result, MemorySegment userData) {
    try {
      Consumer<MemorySegment> pending = PENDING.unregister(userData);
      if (pending != null) {
        pending.accept(result);
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
