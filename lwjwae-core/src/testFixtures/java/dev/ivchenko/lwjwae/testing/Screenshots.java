package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Captures the screen while a display test has its window open, so that CI can attach what the
 * engine drew.
 *
 * <p>Capturing is off unless {@code -Dlwjwae.screenshots=true} is set, because the desktop of a
 * developer isn't worth capturing. The whole screen is captured instead of the window alone. On
 * Xvfb, the window is the only thing on the screen, and on a real desktop, the surroundings help
 * explain a failure.
 *
 * <p>The picture comes from a tool of the system, never from {@code java.awt.Robot}: on Xvfb,
 * {@code Robot} showed a GTK dialog as a black box that was drawn in full, and on macOS, AWT would
 * bring a second {@code NSApplication} into a process that already runs one. X11 goes through
 * {@code import} of ImageMagick, a wlroots compositor through {@code grim}, Windows through
 * PowerShell and {@code System.Drawing}, and macOS through {@code screencapture}. A Wayland desktop
 * without {@code grim}, such as GNOME or KDE, asks the user for every capture through its portal,
 * so nothing is captured there.
 */
@UtilityClass
public class Screenshots {
  private final boolean ENABLED = Boolean.getBoolean("lwjwae.screenshots");
  private final Path DIRECTORY =
      Path.of(System.getProperty("lwjwae.screenshotsDir", "build/screenshots"));
  private final long PAINT_DELAY_MILLIS = 500;
  private final long CAPTURE_TIMEOUT_SECONDS = 30;

  /**
   * The virtual screen of Windows into the PNG file {@code $file}. The process declares itself
   * aware of the DPI first, so that a scaled desktop is captured whole.
   */
  private final String WINDOWS_CAPTURE =
      """
      Add-Type -AssemblyName System.Windows.Forms, System.Drawing
      Add-Type -Namespace Lwjwae -Name Dpi -MemberDefinition '[DllImport("user32.dll")] public static extern bool SetProcessDPIAware();'
      [Lwjwae.Dpi]::SetProcessDPIAware() | Out-Null
      $screen = [System.Windows.Forms.SystemInformation]::VirtualScreen
      $bitmap = New-Object System.Drawing.Bitmap $screen.Width, $screen.Height
      $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
      $graphics.CopyFromScreen($screen.Left, $screen.Top, 0, 0, $bitmap.Size)
      $bitmap.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
      """;

  /**
   * Saves {@code NAME.png} into the screenshot directory. This method does nothing when screenshots
   * are off.
   *
   * <p>A capture that fails, for want of the tool or of a screen, is reported, not thrown. The
   * picture is diagnostics; the assertions are the test. CI notices a missing picture through the
   * artifact step instead.
   */
  @SneakyThrows
  public void capture(String name) {
    if (!ENABLED) {
      return;
    }
    Thread.sleep(PAINT_DELAY_MILLIS);
    Files.createDirectories(DIRECTORY);
    Path file = DIRECTORY.resolve(name + ".png").toAbsolutePath();
    List<String> command = Screenshots.command(file);
    if (command == null) {
      System.err.println("Screenshot '" + name + "' skipped: no capture tool for this desktop");
      return;
    }
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        System.err.println("Screenshot '" + name + "' skipped: " + command.getFirst() + " hung");
      } else if (process.exitValue() != 0) {
        System.err.println("Screenshot '" + name + "' skipped: " + output.strip());
      }
    } catch (IOException e) {
      System.err.println("Screenshot '" + name + "' skipped: " + e.getMessage());
    }
  }

  /** The command that captures the screen into {@code file}, or {@code null} for none. */
  private List<String> command(Path file) {
    if (PlatformUtil.isMacOs()) {
      return List.of("screencapture", "-x", file.toString());
    }
    if (PlatformUtil.isWindows()) {
      // Encoded, the script reaches PowerShell whole: the command line of Windows would mangle the
      // quotes in it.
      String script = "$file = '" + file.toString().replace("'", "''") + "'\n" + WINDOWS_CAPTURE;
      String encoded =
          Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
      return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }
    if (System.getenv("WAYLAND_DISPLAY") != null) {
      return Screenshots.isOnPath("grim") ? List.of("grim", file.toString()) : null;
    }
    return List.of("import", "-window", "root", file.toString());
  }

  private boolean isOnPath(String tool) {
    String path = System.getenv("PATH");
    if (path == null) {
      return false;
    }
    for (String directory : path.split(File.pathSeparator)) {
      if (Files.isExecutable(Path.of(directory, tool))) {
        return true;
      }
    }
    return false;
  }
}
