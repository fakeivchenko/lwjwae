package dev.ivchenko.lwjwae.update;

import java.io.IOException;
import java.nio.file.Path;

/**
 * How one kind of package installs an update over the running application.
 *
 * <p>A running executable can't be replaced safely, so {@link #install} starts a small process of
 * the system shell that waits for the application to exit, puts the new version in place, and
 * starts it. The application quits right after.
 */
interface UpdateInstaller {
  /** Whether this process can install an update itself. */
  boolean isInstallable();

  /** Where the file of an update is downloaded to. */
  Path downloadDirectory() throws IOException;

  /**
   * Starts the process that installs {@code file} once process {@code pid} has exited, and starts
   * the new version.
   */
  void install(Path file, long pid) throws IOException;
}
