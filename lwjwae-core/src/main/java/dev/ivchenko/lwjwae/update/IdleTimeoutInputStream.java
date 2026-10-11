package dev.ivchenko.lwjwae.update;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * A body of the HTTP client that fails once nothing arrived for a while.
 *
 * <p>A request timeout of the HTTP client ends at the headers, so a server that sends them and then
 * stops would hold a read of the body, and the update with it, forever. A virtual thread watches
 * the time since the last byte and interrupts the thread that reads, which the body of the client
 * answers with an {@link IOException}; this stream turns that into one that says what happened.
 *
 * <p>The stream belongs to the thread that creates it, which is the one that reads it and closes
 * it. Closing it stops the watch, and clears the interrupt that the watch left behind.
 */
final class IdleTimeoutInputStream extends FilterInputStream {
  private final Thread reader = Thread.currentThread();
  private final Duration timeout;
  private final URI url;
  private final Thread watcher;

  private volatile long lastRead = System.nanoTime();
  private volatile boolean stalled;
  private volatile boolean closed;

  /**
   * Watches {@code in}, the body of {@code url}, from now on.
   *
   * @param timeout The longest time without a byte.
   */
  IdleTimeoutInputStream(InputStream in, Duration timeout, URI url) {
    super(in);
    this.timeout = timeout;
    this.url = url;
    this.watcher = Thread.ofVirtual().name("lwjwae-update-watch").start(this::watch);
  }

  @Override
  public int read() throws IOException {
    try {
      int read = super.read();
      this.lastRead = System.nanoTime();
      return read;
    } catch (IOException e) {
      throw this.explained(e);
    }
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    try {
      int read = super.read(buffer, offset, length);
      this.lastRead = System.nanoTime();
      return read;
    } catch (IOException e) {
      throw this.explained(e);
    }
  }

  @Override
  public void close() throws IOException {
    this.closed = true;
    this.watcher.interrupt();
    try {
      super.close();
    } finally {
      if (this.stalled && Thread.currentThread() == this.reader) {
        Thread.interrupted();
      }
    }
  }

  private IOException explained(IOException failure) {
    return this.stalled
        ? new IOException("Nothing arrived from " + this.url + " for " + this.timeout, failure)
        : failure;
  }

  private void watch() {
    long limit = this.timeout.toNanos();
    try {
      while (!this.closed) {
        long idle = System.nanoTime() - this.lastRead;
        if (idle >= limit) {
          this.stalled = true;
          this.reader.interrupt();
          return;
        }
        Thread.sleep(Duration.ofNanos(limit - idle));
      }
    } catch (InterruptedException _) {
      // Closed: the read is over.
    }
  }
}
