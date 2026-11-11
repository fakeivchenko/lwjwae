package dev.ivchenko.lwjwae.update;

/**
 * A version newer than the running one, which the manifest offers for this platform.
 *
 * @param version The version, such as {@code 1.4.0}.
 * @param notes What changed, as the manifest says it; empty for nothing.
 * @param mandatory Whether the running version is older than the {@code minimumVersion} of the
 *     manifest, which the application decides what to make of.
 * @param artifact The file that installs it.
 * @param installable Whether this process can install it itself: a native executable from an {@code
 *     .msi} on Windows, a bundle in a directory that it may write on macOS, and an AppImage on
 *     Linux. A Debian or Arch package updates through its package manager instead.
 */
public record Update(
    String version,
    String notes,
    boolean mandatory,
    UpdateArtifact artifact,
    boolean installable) {}
