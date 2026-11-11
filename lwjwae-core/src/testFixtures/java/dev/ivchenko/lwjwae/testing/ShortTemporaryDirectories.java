package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;

/**
 * Temporary directories short enough for a Unix domain socket inside: under {@code /tmp} rather
 * than the temporary directory of macOS, whose path takes most of the 104 bytes that a socket path
 * may have. Windows allows more, so its own temporary directory stays.
 */
public class ShortTemporaryDirectories implements TempDirFactory {
  @Override
  public Path createTempDirectory(
      AnnotatedElementContext elementContext, ExtensionContext extensionContext)
      throws IOException {
    return PlatformUtil.isWindows()
        ? Files.createTempDirectory("lwjwae")
        : Files.createTempDirectory(Path.of("/tmp"), "lwjwae");
  }
}
