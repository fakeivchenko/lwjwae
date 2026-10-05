package dev.ivchenko.lwjwae.event;

import dev.ivchenko.lwjwae.Window;
import java.nio.file.Path;
import java.util.List;

/**
 * The user dropped files from the file manager of the desktop onto the page of a window, see {@link
 * Window#onFileDrop}.
 *
 * <p>Platforms:
 *
 * <ul>
 *   <li>Windows: {@code x} and {@code y} are the {@code clientX} and {@code clientY} of the {@code
 *       drop} event of the page, in the pixels of the page at any zoom.
 *   <li>macOS: As on Windows.
 *   <li>Linux, GTK 3: {@code x} and {@code y} are in the pixels of the window, which are the pixels
 *       of the page only at a zoom of 1.
 *   <li>Linux, GTK 4: As on Windows.
 * </ul>
 *
 * @param window The window that the files landed on.
 * @param paths The files and folders, in the order that the file manager gave them, as absolute
 *     paths. Never empty.
 * @param x Where the pointer was when the user let go, from the left edge of the page.
 * @param y The same from the top edge of the page.
 */
public record FileDropEvent(Window window, List<Path> paths, int x, int y) {
  public FileDropEvent {
    paths = List.copyOf(paths);
  }
}
