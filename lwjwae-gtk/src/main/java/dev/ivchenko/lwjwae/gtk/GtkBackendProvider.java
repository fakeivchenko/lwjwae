package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.BackendProvider;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers the GTK 3 and WebKitGTK 4.1 backend with {@link dev.ivchenko.lwjwae.Application}.
 *
 * <p>The provider is discovered through {@code META-INF/services}, so the presence of this JAR file
 * on the classpath makes the backend available on Linux and the BSDs.
 *
 * <p>When a library is missing, {@link #unsupportedReason()} names it and, for the distributions
 * that the provider knows, the command that installs it. The package names come from the
 * distribution identifiers in {@code /etc/os-release}, so a derivative such as Linux Mint or Rocky
 * Linux gets the hint of its base.
 */
public class GtkBackendProvider implements BackendProvider {
  private static final String[] GTK = {"libgtk-3.so.0", "libgtk-3.so"};
  private static final String[] WEBKIT = {"libwebkit2gtk-4.1.so.0", "libwebkit2gtk-4.1.so"};

  /**
   * The install command of both libraries, by distribution identifier. The order matters only for
   * the lookup: the first identifier of the machine that appears here wins.
   */
  private static final Map<String, String> INSTALL_COMMANDS =
      Map.of(
          "ubuntu", "sudo apt install libgtk-3-0t64 libwebkit2gtk-4.1-0",
          "debian", "sudo apt install libgtk-3-0 libwebkit2gtk-4.1-0",
          "fedora", "sudo dnf install gtk3 webkit2gtk4.1",
          "rhel", "sudo dnf install gtk3 webkit2gtk4.1",
          "arch", "sudo pacman -S gtk3 webkit2gtk-4.1",
          "suse", "sudo zypper install libgtk-3-0 libwebkit2gtk-4_1-0",
          "opensuse", "sudo zypper install libgtk-3-0 libwebkit2gtk-4_1-0",
          "alpine", "sudo apk add gtk+3.0 webkit2gtk-4.1",
          "void", "sudo xbps-install gtk+3 webkit2gtk",
          "gentoo", "sudo emerge x11-libs/gtk+:3 net-libs/webkit-gtk:4.1");

  @Override
  public String name() {
    return "gtk3-webkit2gtk-4.1";
  }

  @Override
  public boolean isSupported() {
    // Check the cheap thing first: probing libraries means dlopen, and webkit2gtk is ~90 MB.
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
        .orElse(reason + " (GTK 3 and WebKitGTK 4.1, that is, WebKitGTK 2.40 or newer)");
  }

  @Override
  public Application create(ApplicationParameters parameters) {
    return new GtkApplication(parameters);
  }
}
