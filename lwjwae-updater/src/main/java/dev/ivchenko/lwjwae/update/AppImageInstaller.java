package dev.ivchenko.lwjwae.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Installs an AppImage over the one that runs, which the AppImage runtime names in {@code
 * $APPIMAGE}.
 *
 * <p>The new file is downloaded next to the old one, so the rename that replaces it is atomic: the
 * application is the old one or the new one, never half of either.
 */
final class AppImageInstaller implements UpdateInstaller {
  /** Waits for {@code $1} to exit, moves {@code $2} over {@code $3}, and starts {@code $3}. */
  private static final String SCRIPT =
      """
      while kill -0 "$1" 2>/dev/null; do sleep 0.2; done
      chmod +x "$2" && mv -f "$2" "$3"
      exec "$3"
      """;

  private final Path appImage;

  /**
   * Installs over {@code appImage}.
   *
   * @param appImage The AppImage that runs, or {@code null} when the application isn't one.
   */
  AppImageInstaller(Path appImage) {
    this.appImage = appImage;
  }

  @Override
  public boolean isInstallable() {
    return this.appImage != null
        && this.appImage.getParent() != null
        && Files.isWritable(this.appImage.getParent());
  }

  @Override
  public Path downloadDirectory() {
    return this.appImage.getParent();
  }

  @Override
  public void install(Path file, long pid) throws IOException {
    UpdateProcesses.startDetached(
        "/bin/sh",
        "-c",
        SCRIPT,
        "lwjwae-update",
        Long.toString(pid),
        file.toString(),
        this.appImage.toString());
  }
}
