package dev.ivchenko.lwjwae.glib.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Pipes, and the GIO streams on top of them: how Java hands an engine a response that it's still
 * writing.
 *
 * <p>The engine reads a {@code GInputStream}, and implementing one in Java would mean a GObject
 * subclass. A pipe does the same with no class at all: the engine reads a {@code GUnixInputStream}
 * on one end, Java writes to the other end when it has data, and a closed read end, which is what
 * an abandoned request leaves, turns the next write into {@code EPIPE}. The JVM ignores {@code
 * SIGPIPE}, so the write fails instead of the process.
 */
@UtilityClass
public class Unix {
  private final SymbolLookup LIBC = NativeLibraries.load("libc.so.6");
  private final SymbolLookup GIO = NativeLibraries.load("libgio-2.0.so.0", "libgio-2.0.so");

  private final int O_CLOEXEC = 0x80000;

  private final MethodHandle PIPE2 =
      NativeLibraries.downcall(LIBC, "pipe2", Signatures.INT_POINTER_INT);
  private final MethodHandle WRITE =
      NativeLibraries.downcall(LIBC, "write", Signatures.LONG_INT_POINTER_LONG);
  private final MethodHandle CLOSE = NativeLibraries.downcall(LIBC, "close", Signatures.INT_INT);
  private final MethodHandle UNIX_INPUT_STREAM_NEW =
      NativeLibraries.downcall(GIO, "g_unix_input_stream_new", Signatures.POINTER_INT_INT);
  private final MethodHandle INPUT_STREAM_READ_ALL =
      NativeLibraries.downcall(GIO, "g_input_stream_read_all", Signatures.G_INPUT_STREAM_READ_ALL);

  /**
   * Opens a pipe.
   *
   * @return {@code {read, write}}, the two file descriptors.
   */
  @SneakyThrows
  public int[] pipe() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment fds = arena.allocate(ValueLayout.JAVA_INT, 2);
      if ((int) PIPE2.invokeExact(fds, O_CLOEXEC) != 0) {
        throw new IllegalStateException("pipe2 failed");
      }
      return new int[] {
        fds.getAtIndex(ValueLayout.JAVA_INT, 0), fds.getAtIndex(ValueLayout.JAVA_INT, 1)
      };
    }
  }

  /**
   * Writes all of {@code data} to {@code fd}, blocking while the pipe is full.
   *
   * @return False if the read end is closed: nobody reads anymore.
   */
  @SneakyThrows
  public boolean writeAll(int fd, byte[] data) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
      long offset = 0;
      while (offset < data.length) {
        long written =
            (long) WRITE.invokeExact(fd, buffer.asSlice(offset), (long) data.length - offset);
        if (written < 0) {
          return false;
        }
        offset += written;
      }
      return true;
    }
  }

  /** Closes a file descriptor. */
  @SneakyThrows
  public void close(int fd) {
    int _ = (int) CLOSE.invokeExact(fd);
  }

  /** A {@code GInputStream} that reads {@code fd} and closes it when the stream goes. */
  @SneakyThrows
  public MemorySegment inputStream(int fd) {
    return (MemorySegment) UNIX_INPUT_STREAM_NEW.invokeExact(fd, 1);
  }

  /** Reads {@code stream} to its end. Blocks; call it off the UI thread. */
  @SneakyThrows
  public byte[] readAll(MemorySegment stream) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate(65536);
      MemorySegment read = arena.allocate(ValueLayout.JAVA_LONG);
      while (true) {
        int ok =
            (int)
                INPUT_STREAM_READ_ALL.invokeExact(
                    stream, buffer, 65536L, read, MemorySegment.NULL, MemorySegment.NULL);
        long count = read.get(ValueLayout.JAVA_LONG, 0);
        out.write(buffer.asSlice(0, count).toArray(ValueLayout.JAVA_BYTE), 0, (int) count);
        if (ok == 0 || count < 65536) {
          return out.toByteArray();
        }
      }
    }
  }
}
