package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Captures the screen while a display test has its window open, so that CI can attach what the
 * engine drew.
 *
 * <p>Capturing is off unless {@code -Dlwjwae.screenshots=true} is set, because the desktop of a
 * developer isn't worth capturing. The whole virtual screen is captured instead of the window
 * alone. On Xvfb, the window is the only thing on the screen, and on a real desktop, the
 * surroundings help explain a failure. {@code java.awt.Robot} needs nothing beyond the X libraries
 * that WebKitGTK already depends on, plus {@code libXtst}. On macOS, the {@code screencapture} tool
 * of the system is used instead, because AWT would bring a second {@code NSApplication} into a
 * process that already runs one.
 *
 * <p>On X11, {@code Robot} captures through GTK when it can, and the GTK it loads is GTK 3. In a
 * process that runs GTK 4, the two register the same GDK types, GLib warns {@code cannot register
 * existing type 'GdkDisplayManager'}, and the process hangs soon after. {@code awt.robot.gtk=false}
 * makes {@code Robot} read the screen through Xlib instead, which works under either toolkit.
 */
@UtilityClass
public class Screenshots {
  private final boolean ENABLED = Boolean.getBoolean("lwjwae.screenshots");
  private final Path DIRECTORY =
      Path.of(System.getProperty("lwjwae.screenshotsDir", "build/screenshots"));
  private final long PAINT_DELAY_MILLIS = 500;

  static {
    // Read once, when Robot's X11 peer loads: keep it from loading GTK 3 into a GTK 4 process.
    if (System.getProperty("awt.robot.gtk") == null) {
      System.setProperty("awt.robot.gtk", "false");
    }
  }

  /**
   * Saves {@code NAME.png} into the screenshot directory. This method does nothing when screenshots
   * are off.
   *
   * <p>A capture that the desktop refuses, for example on Wayland without a portal grant or in a
   * locked session, is reported, not thrown. The picture is diagnostics; the assertions are the
   * test. CI notices a missing picture through the artifact step instead.
   */
  @SneakyThrows
  public void capture(String name) {
    if (!ENABLED) {
      return;
    }
    if (PlatformUtil.isLinux() && System.getenv("WAYLAND_DISPLAY") != null) {
      // Robot would go through the desktop portal, which asks the user on every capture; CI runs on
      // Xvfb.
      System.err.println("Screenshot '" + name + "' skipped: no capture on Wayland");
      return;
    }

    Thread.sleep(PAINT_DELAY_MILLIS);
    Files.createDirectories(DIRECTORY);
    Path file = DIRECTORY.resolve(name + ".png");
    if (PlatformUtil.isMacOs()) {
      new ProcessBuilder("screencapture", "-x", file.toString()).inheritIO().start().waitFor();
      return;
    }
    try {
      Rectangle screen = new Rectangle();
      for (GraphicsDevice device :
          GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
        screen = screen.union(device.getDefaultConfiguration().getBounds());
      }
      ImageIO.write(new Robot().createScreenCapture(screen), "png", file.toFile());
    } catch (SecurityException | AWTException e) {
      System.err.println("Screenshot '" + name + "' skipped: " + e.getMessage());
    }
  }
}
