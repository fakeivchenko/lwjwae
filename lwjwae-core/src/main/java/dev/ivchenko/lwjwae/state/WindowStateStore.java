package dev.ivchenko.lwjwae.state;

import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

/**
 * The file where the windows of an application keep their {@link SavedWindowState}: {@code
 * window-state.properties} in the data directory of the application, one group of keys per state
 * key of a window.
 *
 * <p>A file that is missing or damaged is no reason to fail the application: the window then opens
 * as its parameters say, and the next close writes a good file. A write goes to a temporary file
 * first and replaces the old one in one step, so a crash in the middle leaves the old state.
 */
public final class WindowStateStore {
  private static final String FILE_NAME = "window-state.properties";

  private final Path file;

  /**
   * @param directory The data directory of the application, created when a state is saved.
   */
  public WindowStateStore(Path directory) {
    this.file = directory.resolve(FILE_NAME);
  }

  /** The state saved under {@code key}, if there's a whole one. */
  public synchronized Optional<SavedWindowState> load(String key) {
    Properties properties = this.read();
    try {
      String width = properties.getProperty(key + ".width");
      String height = properties.getProperty(key + ".height");
      if (width == null || height == null) {
        return Optional.empty();
      }
      String x = properties.getProperty(key + ".x");
      String y = properties.getProperty(key + ".y");
      return Optional.of(
          new SavedWindowState(
              Integer.parseInt(width),
              Integer.parseInt(height),
              x == null ? null : Integer.valueOf(x),
              y == null ? null : Integer.valueOf(y),
              Boolean.parseBoolean(properties.getProperty(key + ".maximized"))));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  /** Saves {@code state} under {@code key}, next to the states of the other windows. */
  public synchronized void save(String key, SavedWindowState state) {
    Properties properties = this.read();
    properties.setProperty(key + ".width", Integer.toString(state.width()));
    properties.setProperty(key + ".height", Integer.toString(state.height()));
    if (state.hasPosition()) {
      properties.setProperty(key + ".x", Integer.toString(state.x()));
      properties.setProperty(key + ".y", Integer.toString(state.y()));
    } else {
      properties.remove(key + ".x");
      properties.remove(key + ".y");
    }
    properties.setProperty(key + ".maximized", Boolean.toString(state.maximized()));
    try {
      Files.createDirectories(this.file.getParent());
      Path temporary = Files.createTempFile(this.file.getParent(), FILE_NAME, ".tmp");
      try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        properties.store(writer, "The size and place of the windows of the application");
      }
      Files.move(
          temporary,
          this.file,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      ThrowableUtil.report(new UncheckedIOException("Could not save " + this.file, e));
    }
  }

  private Properties read() {
    Properties properties = new Properties();
    if (Files.isRegularFile(this.file)) {
      try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
        properties.load(reader);
      } catch (IOException | IllegalArgumentException _) {
        properties.clear();
      }
    }
    return properties;
  }
}
