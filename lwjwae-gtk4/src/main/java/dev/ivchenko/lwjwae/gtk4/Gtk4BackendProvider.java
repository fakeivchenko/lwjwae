package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.BackendProvider;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers the GTK 4 and WebKitGTK 6.0 backend with {@link dev.ivchenko.lwjwae.Application}.
 *
 * <p>The provider is discovered through {@code META-INF/services}, so the presence of this JAR file
 * on the classpath makes the backend available on Linux and the BSDs.
 *
 * <p>GTK 4 can't place a window, which GTK 3 can on X11, so the priority of this backend is below
 * that of the GTK 3 one: with both on the classpath and both libraries installed, GTK 3 wins, and
 * GTK 4 runs where only it is available, as on distributions that ship WebKitGTK 6.0 without 4.1.
 * {@code -Dlwjwae.backend=gtk4-webkitgtk-6.0} picks it anyway.
 *
 * <p>When a library is missing, {@link #unsupportedReason()} names it and, for the distributions
 * that the provider knows, the command that installs it. The package names come from the
 * distribution identifiers in {@code /etc/os-release}, so a derivative such as Linux Mint or Rocky
 * Linux gets the hint of its base.
 */
public class Gtk4BackendProvider implements BackendProvider {
  private static final String[] GTK = {"libgtk-4.so.1", "libgtk-4.so"};
  private static final String[] WEBKIT = {"libwebkitgtk-6.0.so.4", "libwebkitgtk-6.0.so"};

  /** Below the GTK 3 backend, whose priority is the default, 0. */
  private static final int PRIORITY = -10;

  /**
   * The install command of both libraries, by distribution identifier. The order matters only for
   * the lookup: the first identifier of the machine that appears here wins.
   */
  private static final Map<String, String> INSTALL_COMMANDS =
      Map.of(
          "ubuntu", "sudo apt install libgtk-4-1 libwebkitgtk-6.0-4",
          "debian", "sudo apt install libgtk-4-1 libwebkitgtk-6.0-4",
          "fedora", "sudo dnf install gtk4 webkitgtk6.0",
          "rhel", "sudo dnf install gtk4 webkitgtk6.0",
          "arch", "sudo pacman -S gtk4 webkitgtk-6.0",
          "suse", "sudo zypper install libgtk-4-1 libwebkitgtk-6_0-4",
          "opensuse", "sudo zypper install libgtk-4-1 libwebkitgtk-6_0-4",
          "alpine", "sudo apk add gtk4.0 webkit2gtk-6.0",
          "void", "sudo xbps-install gtk4 libwebkitgtk60",
          "gentoo", "sudo emerge gui-libs/gtk:4 net-libs/webkit-gtk:6");

  @Override
  public String name() {
    return "gtk4-webkitgtk-6.0";
  }

  @Override
  public int priority() {
    return PRIORITY;
  }

  @Override
  public boolean isSupported() {
    // Check the cheap thing first: probing libraries means dlopen, and webkitgtk is ~90 MB.
    if (!PlatformUtil.isUnixDesktop()) {
      return false;
    }

    return NativeLibraries.isLoadable(GTK) && NativeLibraries.isLoadable(WEBKIT);
  }

  @Override
  public String unsupportedReason() {
    if (!PlatformUtil.isUnixDesktop()) {
      return "runs on Linux and the BSDs, not on " + PlatformUtil.osName();
    }

    List<String> missing = new ArrayList<>();
    if (!NativeLibraries.isLoadable(GTK)) {
      missing.add(GTK[0]);
    }
    if (!NativeLibraries.isLoadable(WEBKIT)) {
      missing.add(WEBKIT[0]);
    }
    if (missing.isEmpty()) {
      return "is supported";
    }

    String reason = "can't load " + String.join(" and ", missing);
    return PlatformUtil.linuxDistributionIds().stream()
        .filter(INSTALL_COMMANDS::containsKey)
        .findFirst()
        .map(id -> reason + "; install them with: " + INSTALL_COMMANDS.get(id))
        .orElse(reason + " (GTK 4 and WebKitGTK 6.0, that is, WebKitGTK 2.40 or newer)");
  }

  @Override
  public Application create(ApplicationParameters parameters) {
    return new Gtk4Application(parameters);
  }
}
