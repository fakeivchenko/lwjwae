package dev.ivchenko.lwjwae.event;

/**
 * The stage of a page load, modeled after the WebKit load events, so that every backend reports the
 * same lifecycle.
 */
public enum LoadState {
  /** A new load has been requested and started. */
  STARTED,
  /** The load was redirected to a different URL. */
  REDIRECTED,
  /** The response was received and the content is about to be rendered. */
  COMMITTED,
  /** The load completed successfully. */
  FINISHED,
  /** The load was aborted with an error. */
  FAILED
}
