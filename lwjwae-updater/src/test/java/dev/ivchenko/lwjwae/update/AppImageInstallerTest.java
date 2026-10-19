package dev.ivchenko.lwjwae.update;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class AppImageInstallerTest {
  @Test
  @EnabledOnOs(OS.LINUX)
  void theNewAppImageReplacesTheOldOneOnceTheApplicationExitsAndStarts(@TempDir Path directory)
      throws Exception {
    Path appImage = directory.resolve("App.AppImage");
    Path started = directory.resolve("started");
    Files.writeString(appImage, "#!/bin/sh\necho old > '" + started + "'\n");
    Path update = directory.resolve(".lwjwae-update-App.AppImage");
    Files.writeString(update, "#!/bin/sh\necho new > '" + started + "'\n");
    AppImageInstaller installer = new AppImageInstaller(appImage);
    Assertions.assertTrue(installer.isInstallable());
    Assertions.assertEquals(directory, installer.downloadDirectory());

    Process application = new ProcessBuilder("sleep", "1").start();
    installer.install(update, application.pid());
    Thread.sleep(300);
    Assertions.assertFalse(Files.exists(started), "waits for the application to exit");
    Assertions.assertTrue(application.waitFor(5, TimeUnit.SECONDS));
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!Files.exists(started) && System.nanoTime() < deadline) {
      Thread.sleep(50);
    }
    Assertions.assertEquals("new\n", Files.readString(started, StandardCharsets.UTF_8));
    Assertions.assertFalse(Files.exists(update));
    Assertions.assertTrue(Files.isExecutable(appImage));
  }

  @Test
  void applicationOutsideAppImageCanNotInstall() {
    Assertions.assertFalse(new AppImageInstaller(null).isInstallable());
  }

  @Test
  void bundleOfMacExecutableIsItsAppDirectory() {
    Assertions.assertEquals(
        Path.of("/Applications/Notes.app"),
        MacAppInstaller.bundleOf(Path.of("/Applications/Notes.app/Contents/MacOS/notes")));
    Assertions.assertNull(MacAppInstaller.bundleOf(Path.of("/usr/bin/java")));
  }

  @Test
  void architecturesHaveTheNamesOfTheManifest() {
    Assertions.assertEquals("x64", UpdatePlatform.architecture("amd64"));
    Assertions.assertEquals("arm64", UpdatePlatform.architecture("aarch64"));
    Assertions.assertTrue(UpdatePlatform.key().matches("(windows|macos|linux)-\\w+"));
  }
}
