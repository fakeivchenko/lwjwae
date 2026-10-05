package dev.ivchenko.lwjwae.event;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * The system asks the application to open links or files: a link of a scheme that the application
 * registered, such as {@code notes://today}, or a file of a type that it registered, which the user
 * opened from the file manager. See {@link dev.ivchenko.lwjwae.Application#onOpen}.
 *
 * @param urls The links, in the order that the system gave them; never a {@code file:} URL.
 * @param files The files and folders, as absolute paths, in the order that the system gave them.
 */
public record OpenEvent(List<URI> urls, List<Path> files) {
  public OpenEvent {
    urls = List.copyOf(urls);
    files = List.copyOf(files);
  }

  /** Whether the event asks for nothing: no link and no file. */
  public boolean isEmpty() {
    return this.urls.isEmpty() && this.files.isEmpty();
  }
}
