package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.util.PlatformUtil;

/**
 * The service that a backend module registers so that {@link Application#create} can find it.
 *
 * <p>A provider is asked two things before anything native loads: whether it supports the machine,
 * and how much it wants to be chosen. {@link #isSupported()} must be cheap and side-effect free,
 * because every provider on the classpath is asked, the ones that lose included: check the
 * operating system first, then whether the native libraries load, and never initialize a toolkit.
 */
public interface BackendProvider {
  /** A short stable name, such as {@code gtk3-webkit2gtk-4.1}, for logs and for the override. */
  String name();

  /** Whether this backend can run on this machine. */
  boolean isSupported();

  /**
   * One clause that says why {@link #isSupported()} is {@code false}, for the message of {@link
   * dev.ivchenko.lwjwae.exception.BackendNotAvailableException}. No trailing period.
   */
  default String unsupportedReason() {
    return "does not support " + PlatformUtil.osName();
  }

  /** Higher wins when several providers support the machine. */
  default int priority() {
    return 0;
  }

  /** Creates the application. Called once the provider was chosen. */
  Application create(ApplicationParameters parameters);
}
