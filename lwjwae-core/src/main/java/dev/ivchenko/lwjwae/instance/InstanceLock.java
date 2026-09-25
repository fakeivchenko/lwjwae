package dev.ivchenko.lwjwae.instance;

import dev.ivchenko.lwjwae.event.SecondInstanceEvent;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The claim of one process on the name of an application, and the line through which the processes
 * started after it hand their start over to it.
 *
 * <p>The line is a Unix domain socket, which Java has on Linux, macOS, and Windows 10 and later
 * alike, in a directory of the user: {@code $XDG_RUNTIME_DIR} on Linux, the temporary directory
 * elsewhere. Its name is a hash of the user and the name of the application, which keeps it short,
 * as the address of such a socket must be, and keeps two users apart where the directory is shared.
 * The first process binds it and accepts. A later one connects, sends its arguments and its working
 * directory, waits until the first one answers that it handled them, and ends.
 *
 * <p>Two processes that start at the same moment would both find no one to connect to, so a claim
 * runs under a lock on a file next to the socket. A socket file stays when its process ends, since
 * removing it on the way out could remove the socket of a process that claimed the name in the
 * meantime; it takes no connection, and the next claim replaces it.
 */
public final class InstanceLock implements AutoCloseable {
  private static final int VERSION = 1;
  private static final byte HANDLED = 1;
  private static final int ARGUMENT_LIMIT = 64 * 1024;
  private static final int TEXT_LIMIT = 1024 * 1024;
  private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(30);

  /** The longest socket path that every platform takes: macOS stops at 104 bytes, NUL included. */
  private static final int PATH_LIMIT = 100;

  /** The bytes that the name of the socket adds to its directory, the separator included. */
  private static final int NAME_LENGTH = "/lwjwae-0123456789abcdef.sock".length();

  private final ServerSocketChannel server;

  private InstanceLock(ServerSocketChannel server) {
    this.server = server;
  }

  /**
   * Claims {@code name} for this process, or hands {@code start} to the process that holds it.
   *
   * @param name The name of the application.
   * @param start What this process was started with.
   * @return The claim, which {@link #serve} opens to the processes that come after, or empty when
   *     another process holds the name and handled {@code start}.
   * @throws UncheckedIOException If the socket can't be made, or the process that holds it doesn't
   *     answer within 30 seconds.
   */
  public static Optional<InstanceLock> claim(String name, SecondInstanceEvent start) {
    return InstanceLock.claim(InstanceLock.defaultDirectory(), name, start);
  }

  /** {@link #claim(String, SecondInstanceEvent)} with the socket in {@code directory}. */
  static Optional<InstanceLock> claim(Path directory, String name, SecondInstanceEvent start) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(start, "start");
    String base = "lwjwae-" + InstanceLock.hash(System.getProperty("user.name", "") + '\0' + name);
    Path socket = directory.resolve(base + ".sock");
    try {
      Files.createDirectories(directory);
      try (FileChannel lockFile =
              FileChannel.open(
                  directory.resolve(base + ".lock"),
                  StandardOpenOption.CREATE,
                  StandardOpenOption.WRITE);
          FileLock _ = lockFile.lock()) {
        if (InstanceLock.forward(socket, start)) {
          return Optional.empty();
        }
        Files.deleteIfExists(socket);
        return Optional.of(new InstanceLock(InstanceLock.bind(socket)));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not claim the single instance of " + name, e);
    }
  }

  /**
   * Accepts the starts of the processes that come after, on a thread of its own, and hands each to
   * {@code handler} on a virtual thread. The process that started waits until {@code handler}
   * returns.
   */
  public void serve(Consumer<SecondInstanceEvent> handler) {
    Objects.requireNonNull(handler, "handler");
    Thread.ofPlatform().daemon().name("lwjwae-instance").start(() -> this.accept(handler));
  }

  /** Stops accepting. A process started after this claims the name for itself. Idempotent. */
  @Override
  public void close() {
    try {
      this.server.close();
    } catch (IOException e) {
      ThrowableUtil.report(e);
    }
  }

  private void accept(Consumer<SecondInstanceEvent> handler) {
    while (true) {
      SocketChannel client;
      try {
        client = this.server.accept();
      } catch (IOException e) {
        if (this.server.isOpen()) {
          ThrowableUtil.report(e);
        }
        return;
      }
      Thread.ofVirtual()
          .name("lwjwae-instance-start")
          .start(() -> InstanceLock.answer(client, handler));
    }
  }

  private static void answer(SocketChannel client, Consumer<SecondInstanceEvent> handler) {
    try (client;
        InputStream input = Channels.newInputStream(client)) {
      SecondInstanceEvent start = InstanceLock.decode(new DataInputStream(input));
      try {
        handler.accept(start);
      } catch (Throwable t) {
        ThrowableUtil.report(t);
      }
      // Handled even when the handler failed: without the answer, the process would claim the
      // name for itself and run as a second instance.
      client.write(ByteBuffer.wrap(new byte[] {HANDLED}));
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Hands {@code start} to the process behind {@code socket}. False when no process takes it: there
   * is no socket, the one there is left over, or its process is on the way out.
   */
  private static boolean forward(Path socket, SecondInstanceEvent start) throws IOException {
    if (!Files.exists(socket)) {
      return false;
    }
    SocketChannel channel;
    try {
      channel = SocketChannel.open(UnixDomainSocketAddress.of(socket));
    } catch (IOException _) {
      return false;
    }
    try (channel) {
      Thread watchdog = Thread.ofVirtual().start(() -> InstanceLock.closeLater(channel));
      try {
        ByteBuffer message = ByteBuffer.wrap(InstanceLock.encode(start));
        while (message.hasRemaining()) {
          channel.write(message);
        }
        ByteBuffer answer = ByteBuffer.allocate(1);
        return channel.read(answer) == 1 && answer.get(0) == HANDLED;
      } catch (AsynchronousCloseException e) {
        throw new IOException("The running instance did not answer within " + ANSWER_TIMEOUT, e);
      } finally {
        watchdog.interrupt();
      }
    }
  }

  /** Closes {@code channel} once {@link #ANSWER_TIMEOUT} is over, unless interrupted first. */
  private static void closeLater(SocketChannel channel) {
    try {
      Thread.sleep(ANSWER_TIMEOUT);
      channel.close();
    } catch (InterruptedException _) {
      // Answered in time.
    } catch (IOException e) {
      ThrowableUtil.report(e);
    }
  }

  private static ServerSocketChannel bind(Path socket) throws IOException {
    ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
    try {
      server.bind(UnixDomainSocketAddress.of(socket));
      if (!PlatformUtil.isWindows()) {
        Files.setPosixFilePermissions(socket, PosixFilePermissions.fromString("rw-------"));
      }
      return server;
    } catch (IOException | RuntimeException e) {
      try (server) {
        throw e;
      }
    }
  }

  /** Four bytes of version, then the arguments and the working directory, each counted. */
  private static byte[] encode(SecondInstanceEvent start) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(VERSION);
      output.writeInt(start.arguments().size());
      for (String argument : start.arguments()) {
        InstanceLock.writeText(output, argument);
      }
      InstanceLock.writeText(output, start.workingDirectory().toString());
    }
    return bytes.toByteArray();
  }

  private static SecondInstanceEvent decode(DataInputStream input) throws IOException {
    int version = input.readInt();
    if (version != VERSION) {
      throw new IOException("A second instance spoke version " + version + ", not " + VERSION);
    }
    int count = input.readInt();
    if (count < 0 || count > ARGUMENT_LIMIT) {
      throw new IOException("A second instance sent " + count + " arguments");
    }
    List<String> arguments = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      arguments.add(InstanceLock.readText(input));
    }
    return new SecondInstanceEvent(arguments, Path.of(InstanceLock.readText(input)));
  }

  private static void writeText(DataOutputStream output, String text) throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    output.writeInt(bytes.length);
    output.write(bytes);
  }

  private static String readText(DataInputStream input) throws IOException {
    int length = input.readInt();
    if (length < 0 || length > TEXT_LIMIT) {
      throw new IOException("A second instance sent a text of " + length + " bytes");
    }
    return new String(input.readNBytes(length), StandardCharsets.UTF_8);
  }

  /**
   * {@code $XDG_RUNTIME_DIR} on Linux, when set, which is the user's own; the temporary directory
   * otherwise.
   */
  private static Path defaultDirectory() {
    String runtime = System.getenv("XDG_RUNTIME_DIR");
    if (PlatformUtil.isLinux() && runtime != null && !runtime.isBlank()) {
      return Path.of(runtime);
    }
    Path temporary = Path.of(System.getProperty("java.io.tmpdir"));
    if (!PlatformUtil.isWindows()
        && temporary.toString().getBytes(StandardCharsets.UTF_8).length + NAME_LENGTH
            > PATH_LIMIT) {
      return Path.of("/tmp");
    }
    return temporary;
  }

  /** The first 16 hex digits of the SHA-256 of {@code text}. */
  private static String hash(String text) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every Java runtime has SHA-256", e);
    }
  }
}
