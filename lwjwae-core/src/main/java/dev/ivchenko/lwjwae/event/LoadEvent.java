package dev.ivchenko.lwjwae.event;

import java.util.Objects;

/**
 * A page load lifecycle notification.
 *
 * @param state The stage that the load has reached.
 * @param url The URL that's being loaded, or {@code null} when the backend doesn't know it yet.
 * @param message The failure description. Not {@code null} only for {@link LoadState#FAILED}.
 */
public record LoadEvent(LoadState state, String url, String message) {
  public LoadEvent {
    Objects.requireNonNull(state, "state");
  }

  /** Creates a transition that isn't a failure. */
  public static LoadEvent of(LoadState state, String url) {
    return new LoadEvent(state, url, null);
  }

  /**
   * Creates a {@link LoadState#FAILED} transition that carries the description of the failure from
   * the engine.
   */
  public static LoadEvent failed(String url, String message) {
    return new LoadEvent(LoadState.FAILED, url, message);
  }
}
