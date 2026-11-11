package dev.ivchenko.lwjwae.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** An installer that installs nothing and remembers what it was given. */
class RecordingInstaller implements UpdateInstaller {
  final List<Path> installed = new CopyOnWriteArrayList<>();
  private final Path directory;

  RecordingInstaller(Path directory) {
    this.directory = directory;
  }

  @Override
  public boolean isInstallable() {
    return this.directory != null;
  }

  @Override
  public Path downloadDirectory() {
    return this.directory;
  }

  @Override
  public void install(Path file, long pid) throws IOException {
    this.installed.add(file);
  }
}
