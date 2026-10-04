package dev.ivchenko.lwjwae.event;

import dev.ivchenko.lwjwae.Window;
import java.nio.file.Path;
import java.util.List;

/**
 * The user dropped files from the file manager of the desktop onto the page of a window, see {@link
 * Window#onFileDrop}.
 *
 * @param window The window that the files landed on.
 * @param paths The files and folders, in the order that the file manager gave them, as absolute
 *     paths. Never empty.
 * @param x Where the pointer was when the user let go, from the left edge of the page, in the
 *     pixels that the platform measures its windows in.
 * @param y The same from the top edge of the page.
 */
public record FileDropEvent(Window window, List<Path> paths, int x, int y) {
  public FileDropEvent {
    paths = List.copyOf(paths);
  }
}
