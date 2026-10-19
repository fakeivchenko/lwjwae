package dev.ivchenko.lwjwae.update;

import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.nio.file.Path;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/** Which file of a manifest updates this process, and how it's installed. */
@UtilityClass
public class UpdatePlatform {
  /**
   * The key of the artifacts of this platform in a manifest: {@code windows}, {@code macos}, or
   * {@code linux}, a dash, and {@code x64} or {@code arm64}, such as {@code macos-arm64}.
   */
  public String key() {
    String os = PlatformUtil.isWindows() ? "windows" : PlatformUtil.isMacOs() ? "macos" : "linux";
    return os + "-" + UpdatePlatform.architecture(System.getProperty("os.arch", ""));
  }

  /** {@code x64} or {@code arm64} for the names that Java gives them, else the name itself. */
  String architecture(String osArch) {
    return switch (osArch.toLowerCase(Locale.ROOT)) {
      case "amd64", "x86_64", "x64" -> "x64";
      case "aarch64", "arm64" -> "arm64";
      default -> osArch.toLowerCase(Locale.ROOT);
    };
  }

  /** Whether this process is a native executable of GraalVM rather than a JVM. */
  boolean isNativeImage() {
    return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
  }

  /** The installer of this process: its package decides it. */
  UpdateInstaller installer() {
    Path executable = ProcessHandle.current().info().command().map(Path::of).orElse(null);
    if (PlatformUtil.isWindows()) {
      return new MsiInstaller(executable);
    }
    if (PlatformUtil.isMacOs()) {
      return new MacAppInstaller(
          UpdatePlatform.isNativeImage() && executable != null
              ? MacAppInstaller.bundleOf(executable)
              : null);
    }
    String appImage = System.getenv("APPIMAGE");
    return new AppImageInstaller(appImage == null || appImage.isBlank() ? null : Path.of(appImage));
  }
}
