package dev.ivchenko.lwjwae.cookie;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The cookies of the engine, which every window of the application shares.
 *
 * <p>The engine keeps them in a process of its own and answers later, so every method returns a
 * future. A cookie that the application sets goes with the next request of the page, and a cookie
 * that a site set is here once the response has come.
 */
public interface Cookies {
  /**
   * The cookies that the engine would send with a request to {@code url}.
   *
   * @throws IllegalArgumentException If {@code url} isn't an absolute URL.
   */
  CompletableFuture<List<Cookie>> get(String url);

  /** Every cookie of every site. */
  CompletableFuture<List<Cookie>> getAll();

  /** Sets {@code cookie}, in place of the one of that name, domain, and path, if any. */
  CompletableFuture<Void> set(Cookie cookie);

  /** Deletes the cookie of the name, the domain, and the path of {@code cookie}, if any. */
  CompletableFuture<Void> delete(Cookie cookie);

  /** Deletes every cookie. */
  CompletableFuture<Void> clear();
}
