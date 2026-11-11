package dev.ivchenko.lwjwae.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Installs an {@code .msi} over the application, which the {@code MajorUpgrade} of its package
 * replaces.
 *
 * <p>{@code msiexec /passive} shows only a progress bar, and asks for elevation itself when the
 * package installs for every user. The script waits with {@code ping}, as {@code timeout} refuses
 * to run without a console for its input.
 */
final class MsiInstaller implements UpdateInstaller {
  /**
   * Waits for process {@code %1} to exit, installs {@code %2}, starts {@code %3}, and deletes the
   * package.
   */
  private static final String SCRIPT =
      """
      @echo off\r
      :wait\r
      tasklist /FI "PID eq %1" /NH 2>nul | find " %1 " >nul\r
      if not errorlevel 1 (\r
        ping -n 2 127.0.0.1 >nul\r
        goto wait\r
      )\r
      msiexec /i "%~2" /passive /norestart\r
      start "" "%~3"\r
      del "%~2"\r
      """;

  private final Path executable;

  /**
   * Installs for {@code executable}.
   *
   * @param executable The executable that runs, and that the package installs again.
   */
  MsiInstaller(Path executable) {
    this.executable = executable;
  }

  @Override
  public boolean isInstallable() {
    return UpdatePlatform.isNativeImage() && this.executable != null;
  }

  @Override
  public Path downloadDirectory() throws IOException {
    return Files.createTempDirectory("lwjwae-update");
  }

  @Override
  public void install(Path file, long pid) throws IOException {
    Path script = Files.createTempFile("lwjwae-update", ".cmd");
    Files.writeString(script, SCRIPT, StandardCharsets.US_ASCII);
    UpdateProcesses.startDetached(
        "cmd.exe",
        "/c",
        script.toString(),
        Long.toString(pid),
        file.toString(),
        this.executable.toString());
  }
}
