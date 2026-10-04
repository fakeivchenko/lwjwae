package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.event.OpenEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Finds the links and the files among the arguments of a process.
 *
 * <p>On Windows and Linux, the system opens a link of a scheme or a file of a type by starting the
 * executable with it as an argument, the way the installer registered it, so the arguments are
 * where an application learns what to open. They also carry what the program needs for itself, such
 * as a flag, so only what is clearly one or the other counts: a URI with a scheme of two letters or
 * more, which leaves a Windows drive such as {@code C:} out, and a path that exists.
 */
@UtilityClass
public class OpenArgumentUtil {
  /** A scheme and its colon, as RFC 3986 spells it, of two letters or more to leave out C:. */
  private final Pattern URL_SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]+:.*");

  /**
   * The links and the files among {@code arguments}: a URI of a scheme other than {@code file} is a
   * link, a local {@code file:} URI and a path that exists, relative to {@code workingDirectory},
   * are files. A flag, which starts with {@code -}, and anything else are left out.
   */
  public OpenEvent of(List<String> arguments, Path workingDirectory) {
    List<URI> urls = new ArrayList<>();
    List<Path> files = new ArrayList<>();
    for (String argument : arguments) {
      if (argument.isEmpty() || argument.startsWith("-")) {
        continue;
      }
      if (URL_SCHEME.matcher(argument).matches()) {
        OpenArgumentUtil.addUrl(argument, urls, files);
        continue;
      }
      try {
        Path path = workingDirectory.resolve(argument);
        if (Files.exists(path)) {
          files.add(path.toAbsolutePath().normalize());
        }
      } catch (InvalidPathException _) {
        // Not a path of this platform, so no file to open.
      }
    }
    return new OpenEvent(urls, files);
  }

  /** Adds a link, or the path of a local {@code file:} URI, and leaves out what isn't a URI. */
  private void addUrl(String argument, List<URI> urls, List<Path> files) {
    URI uri;
    try {
      uri = new URI(argument);
    } catch (URISyntaxException _) {
      return;
    }
    if (!"file".equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
      urls.add(uri);
      return;
    }
    files.addAll(FileDropUtil.pathsOfUris(List.of(argument)));
  }
}
