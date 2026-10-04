package dev.ivchenko.lwjwae.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

/** Turns what a file manager hands over in a drop into paths. */
@UtilityClass
public class FileDropUtil {
  /**
   * The paths of the {@code file:} URIs of a {@code text/uri-list}, as the file manager wrote them,
   * with the spaces and the other characters of the names escaped. A URI of another scheme, such as
   * a link to a web page that the user dragged, or one that names a host, which isn't a file of
   * this machine, is left out, and so is a line that isn't a URI.
   */
  public List<Path> pathsOfUris(Collection<String> uris) {
    List<Path> paths = new ArrayList<>();
    for (String text : uris) {
      try {
        URI uri = new URI(text.strip());
        boolean local = uri.getAuthority() == null || uri.getAuthority().isEmpty();
        if ("file".equalsIgnoreCase(uri.getScheme()) && local) {
          paths.add(Path.of(uri));
        }
      } catch (URISyntaxException | IllegalArgumentException _) {
        // Not a file this machine can open.
      }
    }
    return paths;
  }
}
