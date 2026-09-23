package dev.ivchenko.lwjwae.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * PNG images written to files for a native API that takes a path rather than bytes, such as the
 * image of a notification on every platform.
 *
 * <p>The files live in one temporary directory, created on the first image. A file goes away with
 * {@link #delete(Path)} once nothing shows it, and whatever is left goes with {@link #deleteAll()},
 * which the owner calls when it closes. A file that can't be deleted is reported and left in the
 * temporary directory of the system, which is the least harm a failed cleanup can do.
 */
public class TemporaryImages {
  private final String prefix;
  private final AtomicLong ids = new AtomicLong();

  private Path directory;

  /** Creates the store; {@code prefix} starts the name of its directory. */
  public TemporaryImages(String prefix) {
    this.prefix = prefix;
  }

  /**
   * Writes {@code png} to a new file and returns its path.
   *
   * @throws UncheckedIOException If the file can't be written.
   */
  public synchronized Path write(byte[] png) {
    try {
      if (this.directory == null) {
        this.directory = Files.createTempDirectory(this.prefix);
      }
      Path file = this.directory.resolve("image-" + this.ids.incrementAndGet() + ".png");
      Files.write(file, png);
      return file;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Deletes the directory with every file still in it. */
  public synchronized void deleteAll() {
    Path current = this.directory;
    this.directory = null;
    if (current == null) {
      return;
    }
    try (Stream<Path> paths = Files.walk(current)) {
      paths.sorted(Comparator.reverseOrder()).forEach(TemporaryImages::delete);
    } catch (IOException e) {
      ThrowableUtil.report(e);
    }
  }

  /** Deletes one file. {@code null} is ignored. */
  public static void delete(Path file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      ThrowableUtil.report(e);
    }
  }
}
