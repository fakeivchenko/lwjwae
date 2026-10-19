package dev.ivchenko.lwjwae.update;

import java.net.URI;
import java.util.Objects;

/**
 * The file that updates one platform: an {@code .msi} on Windows, the {@code .app} bundle as a ZIP
 * file on macOS, and an AppImage on Linux.
 *
 * @param url Where the file is, relative ones resolved against the manifest.
 * @param sha256 The SHA-256 of the file, in lowercase hexadecimal.
 * @param size The size of the file in bytes.
 */
public record UpdateArtifact(URI url, String sha256, long size) {
  public UpdateArtifact {
    Objects.requireNonNull(url, "url");
    Objects.requireNonNull(sha256, "sha256");
  }
}
