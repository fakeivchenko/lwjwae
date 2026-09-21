package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.util.PlatformUtil;

/**
 * The window implementation of a platform, discovered through {@link java.util.ServiceLoader}.
 *
 * <p>Implementations register themselves in {@code
 * META-INF/services/dev.ivchenko.lwjwae.ApplicationBackendProvider}. The service lookup is what
 * keeps {@code lwjwae-core} free of platform code: a backend JAR file is either present or absent
 * on the runtime classpath.
 */
public interface ApplicationBackendProvider {
  /** Returns a short identifier for diagnostics, for example {@code "gtk3-webkit2gtk-4.1"}. */
  String name();

  /**
   * Checks whether this backend can run on this machine: the operating system matches, and the
   * native libraries that the backend binds are present. This method must not initialize the
   * toolkit or start a thread, because it's called on every candidate, including the ones that
   * aren't chosen.
   *
   * @return True if the backend can open a window here; false otherwise.
   */
  boolean isSupported();

  /**
   * Explains why {@link #isSupported()} returned {@code false}: one sentence that names the missing
   * piece and, where the provider knows it, how to install it. {@link Application} puts the answer
   * of every rejected provider into the message of its {@link
   * dev.ivchenko.lwjwae.exception.BackendNotAvailableException}, so a user without a debugger can
   * tell a missing package from the wrong operating system.
   *
   * <p>The default names the operating system, which is the right answer for a provider whose only
   * check is the platform. A provider that probes native libraries overrides it and lists the ones
   * that didn't load. The method is called only after {@link #isSupported()} returned {@code
   * false}, and it must be as cheap: no toolkit initialization, no threads.
   */
  default String unsupportedReason() {
    return "does not support " + PlatformUtil.osName();
  }

  /**
   * Returns the rank among supported providers. The highest rank wins. Use it when a platform has
   * more than one viable implementation, for example to prefer GTK 4 over GTK 3. Default: {@code
   * 0}.
   */
  default int priority() {
    return 0;
  }

  /**
   * Creates a window. This method is called only after {@link #isSupported()} returned {@code
   * true}.
   */
  ApplicationBackend create(ApplicationParameters parameters);
}
