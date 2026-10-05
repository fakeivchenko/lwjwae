package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.cookie.Cookie;
import dev.ivchenko.lwjwae.cookie.Cookies;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.ComCallback;
import dev.ivchenko.lwjwae.windows.binding.CookieManager;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The cookies of WebView2, through the {@code ICoreWebView2CookieManager} of the profile.
 *
 * <p>WebView2 hands the manager out only through a view, and every view of the application shares
 * one profile, so each call takes the view of any open window and lets go of the manager when it's
 * done: a call with no window open fails. Reading answers in a completion on the UI thread.
 *
 * <p>Setting and deleting return at once, but the network process of WebView2 applies them a moment
 * later, so a read straight after one can still find the cookie as it was. A change therefore
 * completes once a read shows it, or after {@value #VISIBLE_TIMEOUT_MILLIS} ms at most, which keeps
 * the promise of {@link Cookies}: what comes after the change sees it.
 */
public class WindowsCookies implements Cookies {
  /** How long a change waits for a read to show it. */
  private static final long VISIBLE_TIMEOUT_MILLIS = 5000;

  /** How long it waits between two reads. */
  private static final long VISIBLE_POLL_MILLIS = 50;

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
    return this.change(
        manager -> CookieManager.addOrUpdate(manager, cookie),
        cookies ->
            cookies.stream()
                .anyMatch(
                    found ->
                        WindowsCookies.isSame(found, cookie)
                            && found.value().equals(cookie.value())));
  }

  @Override
  public CompletableFuture<Void> delete(Cookie cookie) {
    Objects.requireNonNull(cookie, "cookie");
    return this.change(
        manager -> CookieManager.delete(manager, cookie),
        cookies -> cookies.stream().noneMatch(found -> WindowsCookies.isSame(found, cookie)));
  }

  @Override
  public CompletableFuture<Void> clear() {
    return this.change(CookieManager::deleteAll, List::isEmpty);
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

  /**
   * Runs {@code operation} on the manager, and completes once {@code visible} holds for every
   * cookie of the profile, or the wait is over.
   */
  private CompletableFuture<Void> change(
      Consumer<MemorySegment> operation, Predicate<List<Cookie>> visible) {
    CompletableFuture<Void> changed = new CompletableFuture<>();
    this.dispatcher.post(
        () -> {
          MemorySegment manager = null;
          try {
            manager = CookieManager.of(this.webView.get());
            operation.accept(manager);
          } catch (Throwable t) {
            changed.completeExceptionally(t);
            return;
          } finally {
            Com.release(manager);
          }
          this.awaitVisible(
              visible,
              System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(VISIBLE_TIMEOUT_MILLIS),
              changed);
        });
    return changed;
  }

  /** Reads the cookies until {@code visible} holds or {@code deadline} passes. */
  private void awaitVisible(
      Predicate<List<Cookie>> visible, long deadline, CompletableFuture<Void> changed) {
    this.read("")
        .whenComplete(
            (cookies, failure) -> {
              if (failure != null) {
                changed.completeExceptionally(failure);
              } else if (visible.test(cookies) || System.nanoTime() > deadline) {
                changed.complete(null);
              } else {
                CompletableFuture.delayedExecutor(VISIBLE_POLL_MILLIS, TimeUnit.MILLISECONDS)
                    .execute(() -> this.awaitVisible(visible, deadline, changed));
              }
            });
  }

  /**
   * Whether {@code found} is the cookie of the name, the domain, and the path of {@code wanted}.
   */
  private static boolean isSame(Cookie found, Cookie wanted) {
    return found.name().equals(wanted.name())
        && found.path().equals(wanted.path())
        && WindowsCookies.bareDomain(found.domain())
            .equals(WindowsCookies.bareDomain(wanted.domain()));
  }

  private static String bareDomain(String domain) {
    String lower = domain.toLowerCase(Locale.ROOT);
    return lower.startsWith(".") ? lower.substring(1) : lower;
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
