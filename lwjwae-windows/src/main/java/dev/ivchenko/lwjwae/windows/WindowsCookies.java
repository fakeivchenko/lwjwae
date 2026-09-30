package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.cookie.Cookie;
import dev.ivchenko.lwjwae.cookie.Cookies;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.ComCallback;
import dev.ivchenko.lwjwae.windows.binding.CookieManager;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The cookies of WebView2, through the {@code ICoreWebView2CookieManager} of the profile.
 *
 * <p>WebView2 hands the manager out only through a view, and every view of the application shares
 * one profile, so each call takes the view of any open window and lets go of the manager when it's
 * done: a call with no window open fails. Setting and deleting are synchronous in WebView2; only
 * reading answers later, in a completion on the UI thread.
 */
public class WindowsCookies implements Cookies {
  private final WindowsDispatcher dispatcher;
  private final Supplier<MemorySegment> webView;

  /**
   * Reaches the cookies through the views that {@code webView} gives.
   *
   * @param webView The view of an open window, called on the UI thread.
   */
  WindowsCookies(WindowsDispatcher dispatcher, Supplier<MemorySegment> webView) {
    this.dispatcher = dispatcher;
    this.webView = webView;
  }

  @Override
  public CompletableFuture<List<Cookie>> get(String url) {
    if (!URI.create(url).isAbsolute()) {
      throw new IllegalArgumentException("Not an absolute URL: " + url);
    }
    return this.read(url);
  }

  @Override
  public CompletableFuture<List<Cookie>> getAll() {
    return this.read("");
  }

  @Override
  public CompletableFuture<Void> set(Cookie cookie) {
    Objects.requireNonNull(cookie, "cookie");
    return this.change(manager -> CookieManager.addOrUpdate(manager, cookie));
  }

  @Override
  public CompletableFuture<Void> delete(Cookie cookie) {
    Objects.requireNonNull(cookie, "cookie");
    return this.change(manager -> CookieManager.delete(manager, cookie));
  }

  @Override
  public CompletableFuture<Void> clear() {
    return this.change(CookieManager::deleteAll);
  }

  private CompletableFuture<List<Cookie>> read(String uri) {
    CompletableFuture<List<Cookie>> read = new CompletableFuture<>();
    this.dispatcher.post(
        () -> {
          MemorySegment manager = null;
          try {
            manager = CookieManager.of(this.webView.get());
            MemorySegment handler =
                ComCallback.completion(
                    CookieManager.IID_GET_COOKIES_COMPLETED,
                    (hresult, list) -> WindowsCookies.completeRead(read, hresult, list));
            CookieManager.getCookies(manager, uri, handler);
            Com.release(handler);
          } catch (Throwable t) {
            read.completeExceptionally(t);
          } finally {
            Com.release(manager);
          }
        });
    return read;
  }

  private CompletableFuture<Void> change(Consumer<MemorySegment> operation) {
    CompletableFuture<Void> changed = new CompletableFuture<>();
    this.dispatcher.post(
        () -> {
          MemorySegment manager = null;
          try {
            manager = CookieManager.of(this.webView.get());
            operation.accept(manager);
            changed.complete(null);
          } catch (Throwable t) {
            changed.completeExceptionally(t);
          } finally {
            Com.release(manager);
          }
        });
    return changed;
  }

  private static void completeRead(
      CompletableFuture<List<Cookie>> read, int hresult, MemorySegment list) {
    try {
      Com.check("GetCookies", hresult);
      read.complete(CookieManager.read(list));
    } catch (Throwable t) {
      read.completeExceptionally(t);
    }
  }
}
