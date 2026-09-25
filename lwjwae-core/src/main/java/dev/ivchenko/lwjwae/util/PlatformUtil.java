package dev.ivchenko.lwjwae.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/** Coarse operating system detection. Backend providers use it to rule themselves out cheaply. */
@UtilityClass
public class PlatformUtil {
  private final String OS = PlatformUtil.osName().toLowerCase(Locale.ROOT);

  /** Checks for Windows, any edition. */
  public boolean isWindows() {
    return OS.contains("win");
  }

  /** Checks for macOS. */
  public boolean isMacOs() {
    return OS.contains("mac") || OS.contains("darwin");
  }

  /** Checks for Linux, any distribution. */
  public boolean isLinux() {
    return OS.contains("linux");
  }

  /** Checks for Linux and the BSDs: the systems where a GTK or Qt desktop stack is the norm. */
  public boolean isUnixDesktop() {
    return PlatformUtil.isLinux()
        || OS.contains("bsd")
        || OS.contains("sunos")
        || OS.contains("aix");
  }

  /**
   * Returns the identifiers of the Linux distribution from {@code /etc/os-release}: the value of
   * {@code ID} first, then every value of {@code ID_LIKE}, in the order that the file lists them.
   * For example, {@code [ubuntu, debian]} on Ubuntu, {@code [rocky, rhel, centos, fedora]} on Rocky
   * Linux, and {@code [arch]} on Arch. The list is empty when the file is absent or the system
   * isn't Linux.
   *
   * <p>The {@code ID_LIKE} chain is what makes an install hint work on a derivative: a provider
   * that knows the package name on {@code debian} doesn't need to know every distribution that's
   * built on it.
   */
  public List<String> linuxDistributionIds() {
    if (!PlatformUtil.isLinux()) {
      return List.of();
    }
    return PlatformUtil.distributionIds(PlatformUtil.osRelease());
  }

  /** Parses the lines of an {@code os-release} file as {@link #linuxDistributionIds()} does. */
  public List<String> distributionIds(List<String> osRelease) {
    List<String> ids = new ArrayList<>();
    for (String line : osRelease) {
      int equals = line.indexOf('=');
      if (equals < 0) {
        continue;
      }
      String key = line.substring(0, equals).strip();
      String value = line.substring(equals + 1).strip().replace("\"", "");
      if (key.equals("ID")) {
        ids.addFirst(value);
      } else if (key.equals("ID_LIKE")) {
        ids.addAll(List.of(value.split("\\s+")));
      }
    }
    return List.copyOf(ids);
  }

  private List<String> osRelease() {
    for (String location : new String[] {"/etc/os-release", "/usr/lib/os-release"}) {
      Path file = Path.of(location);
      if (Files.isReadable(file)) {
        try {
          return Files.readAllLines(file);
        } catch (IOException _) {
          return List.of();
        }
      }
    }
    return List.of();
  }

  /** Returns the raw {@code os.name}, for diagnostics. */
  public String osName() {
    return System.getProperty("os.name", "");
  }
}
