package dev.ivchenko.lwjwae.tray;

import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import java.util.List;

/**
 * A tray icon that a backend put up, and the handle to change or remove it.
 *
 * <p>Every method is safe to call from any thread and returns when the tray has the change. The
 * icon lives until {@link #close()} or until the application that created it closes, whichever
 * comes first. While it lives, it keeps {@link dev.ivchenko.lwjwae.Application#run()} running.
 */
public interface Tray extends AutoCloseable {
  /** Replaces the image with PNG bytes. */
  void icon(byte[] png);

  /**
   * Replaces the image with a PNG among the resources of the application, such as {@code
   * "app/tray.png"}.
   *
   * @throws ResourceNotFoundException If the classpath has no such resource.
   */
  default void icon(String resource) {
    this.icon(ResourceUtil.read(resource));
  }

  /** Replaces the hover text. {@code null} removes it. */
  void tooltip(String tooltip);

  /** Replaces the menu. An empty list removes it. */
  void menu(List<TrayMenuItem> menu);

  /** Replaces the menu with {@code menu}, in order. */
  default void menu(TrayMenuItem... menu) {
    this.menu(List.of(menu));
  }

  /** Whether the icon was removed, by {@link #close()} or with its application. */
  boolean isClosed();

  /** Removes the icon. This method is idempotent. */
  @Override
  void close();
}
