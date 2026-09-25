package dev.ivchenko.lwjwae.clipboard;

import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The clipboard of the desktop: what Ctrl+C, or Command-C, copied in any application.
 *
 * <p>A read returns a future, since a toolkit may answer it later: another application owns what
 * was copied and hands it over on request. A write takes the clipboard at once, and what it wrote
 * stays there after the application exits where the desktop keeps it.
 *
 * <p>Platforms:
 *
 * <ul>
 *   <li>Windows: As described.
 *   <li>macOS: As described.
 *   <li>Linux, GTK 3: X11: what was written goes when the application exits, unless a clipboard
 *       manager of the desktop took a copy, as most do. Wayland: the compositor takes a write, and
 *       answers a read, only while a window of the application has the keyboard focus that the user
 *       gave it; otherwise a write is lost and a read finds nothing.
 *   <li>Linux, GTK 4: As on GTK 3.
 * </ul>
 */
public interface Clipboard {
  /** The text on the clipboard, or empty when there is none, such as after an image was copied. */
  CompletableFuture<Optional<String>> readText();

  /** Puts {@code text} on the clipboard, in place of what was there. */
  void writeText(String text);

  /**
   * The image on the clipboard as PNG bytes, whatever form the application that copied it chose, or
   * empty when there is none, such as after text was copied.
   */
  CompletableFuture<Optional<byte[]>> readImage();

  /**
   * Puts the PNG image {@code png} on the clipboard, in place of what was there, in the forms that
   * other applications of the platform read.
   *
   * @throws IllegalArgumentException If {@code png} isn't an image that the platform can read.
   */
  void writeImage(byte[] png);

  /**
   * Puts a PNG among the resources of the application, such as {@code "app/logo.png"}, on the
   * clipboard.
   *
   * @throws ResourceNotFoundException If the classpath has no such resource.
   */
  default void writeImage(String resource) {
    this.writeImage(ResourceUtil.read(resource));
  }
}
