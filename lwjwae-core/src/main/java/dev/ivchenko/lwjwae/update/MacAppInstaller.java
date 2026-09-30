package dev.ivchenko.lwjwae.update;

import dev.ivchenko.lwjwae.exception.UpdateRejectedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Installs an {@code .app} bundle, which comes as a ZIP file, over the bundle that runs.
 *
 * <p>{@code ditto} unpacks it, as it keeps the signature and the symbolic links of a bundle intact.
 * The new bundle must pass {@code codesign --verify}, and, when the running one is signed, carry
 * the same team: an update can't swap the publisher. The swap keeps the old bundle until the new
 * one is in place, and puts it back if the move fails.
 */
final class MacAppInstaller implements UpdateInstaller {
  /**
   * Waits for {@code $1} to exit, moves the bundle {@code $3} aside, moves {@code $2} in its place,
   * and opens {@code $3}.
   */
  private static final String SCRIPT =
      """
      while kill -0 "$1" 2>/dev/null; do sleep 0.2; done
      old="$3.lwjwae-old"
      rm -rf "$old"
      if mv "$3" "$old"; then
        if mv "$2" "$3"; then rm -rf "$old"; else mv "$old" "$3"; fi
      fi
      open "$3"
      """;

  private final Path bundle;

  /**
   * Installs over {@code bundle}.
   *
   * @param bundle The {@code .app} that runs, or {@code null} when the application isn't in one.
   */
  MacAppInstaller(Path bundle) {
    this.bundle = bundle;
  }

  /** The {@code .app} directory that holds {@code executable}, or {@code null}. */
  static Path bundleOf(Path executable) {
    for (Path path = executable; path != null; path = path.getParent()) {
      Path name = path.getFileName();
      if (name != null && name.toString().endsWith(".app")) {
        return path;
      }
    }
    return null;
  }

  @Override
  public boolean isInstallable() {
    return this.bundle != null
        && this.bundle.getParent() != null
        && Files.isWritable(this.bundle.getParent());
  }

  @Override
  public Path downloadDirectory() throws IOException {
    return Files.createTempDirectory("lwjwae-update");
  }

  @Override
  public void install(Path file, long pid) throws IOException {
    Path unpacked = Files.createTempDirectory("lwjwae-update-app");
    UpdateProcesses.run("ditto", "-x", "-k", file.toString(), unpacked.toString());
    Path app;
    try (Stream<Path> children = Files.list(unpacked)) {
      List<Path> bundles =
          children.filter(path -> path.getFileName().toString().endsWith(".app")).toList();
      if (bundles.size() != 1) {
        throw new UpdateRejectedException("The update holds no single .app bundle");
      }
      app = bundles.getFirst();
    }
    try {
      UpdateProcesses.run("codesign", "--verify", "--deep", "--strict", app.toString());
    } catch (IOException e) {
      throw new UpdateRejectedException("The new bundle fails codesign --verify", e);
    }
    String team = MacAppInstaller.team(this.bundle);
    if (team != null && !team.equals(MacAppInstaller.team(app))) {
      throw new UpdateRejectedException("The new bundle is signed by another team than " + team);
    }
    UpdateProcesses.startDetached(
        "/bin/sh",
        "-c",
        SCRIPT,
        "lwjwae-update",
        Long.toString(pid),
        app.toString(),
        this.bundle.toString());
  }

  /** The {@code TeamIdentifier} of the signature of {@code bundle}, or {@code null} for none. */
  private static String team(Path bundle) {
    String details;
    try {
      details = UpdateProcesses.run("codesign", "-d", "--verbose=2", bundle.toString());
    } catch (IOException _) {
      return null;
    }
    return details
        .lines()
        .filter(line -> line.startsWith("TeamIdentifier="))
        .map(line -> line.substring("TeamIdentifier=".length()).strip())
        .filter(value -> !value.isEmpty() && !value.equals("not set"))
        .findFirst()
        .orElse(null);
  }
}
