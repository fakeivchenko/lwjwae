package dev.ivchenko.lwjwae.bridge;

import dev.ivchenko.lwjwae.rpc.RpcCall;
import dev.ivchenko.lwjwae.rpc.RpcStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * The events that Java emits to the page of one window, and the stream that carries them.
 *
 * <p>A page can't be called, only answered, so the bootstrap of every top-level document of a
 * trusted origin opens one call, {@link BridgeProtocol#EVENTS_CALL}, at its start and reads it for
 * as long as it lives; every event is a frame of that answer: four bytes of length, big-endian, and
 * {@code typed␟name␟payload} in UTF-8. The same stream that carries a file carries the events, in
 * order, with the backpressure of the transport, and one write carries every event that queued
 * while the previous one was on its way.
 *
 * <p>A document holds the stream until it goes away or the next document opens its own. Events
 * emitted while no document holds it wait for the next one, at most {@value #PENDING_LIMIT} of
 * them, the oldest dropped first: the page that {@code emit} addresses is the one that is loading,
 * and a page that never opens the stream, of an origin that the window doesn't trust, mustn't make
 * them pile up. An event that a stream failed to deliver, because its document went away, goes back
 * to the front of the line for the next one.
 */
public final class PageEvents {
  private static final int PENDING_LIMIT = 1024;
  private static final int BATCH_LIMIT = 256 * 1024;
  private static final byte[] RETIRED = new byte[0];

  private final Object lock = new Object();
  private final Deque<byte[]> pending = new ArrayDeque<>();

  private BlockingDeque<byte[]> current;

  /** Queues an event for the document that holds the stream, or the next one. */
  public void send(String name, String payload, boolean typed) {
    byte[] frame = PageEvents.frame(name, payload, typed);
    synchronized (this.lock) {
      if (this.current != null) {
        this.current.add(frame);
        return;
      }
      this.pending.add(frame);
      if (this.pending.size() > PENDING_LIMIT) {
        this.pending.removeFirst();
      }
    }
  }

  /**
   * Serves the event stream of a document: runs on the thread of the call until the document goes
   * away, the next one takes over, or the window closes.
   */
  public void serve(RpcCall call) {
    BlockingDeque<byte[]> queue = new LinkedBlockingDeque<>();
    synchronized (this.lock) {
      if (this.current != null) {
        this.current.add(RETIRED);
      }
      queue.addAll(this.pending);
      this.pending.clear();
      this.current = queue;
    }
    List<byte[]> batch = new ArrayList<>();
    try (RpcStream stream = call.stream("application/octet-stream")) {
      while (true) {
        batch.clear();
        batch.add(queue.take());
        PageEvents.drainBatch(queue, batch);
        boolean retired = batch.getLast() == RETIRED;
        if (retired) {
          batch.removeLast();
        }
        if (!batch.isEmpty() && !stream.write(PageEvents.join(batch))) {
          this.requeue(queue, batch);
          return;
        }
        if (retired) {
          return;
        }
      }
    } catch (InterruptedException _) {
      this.requeue(queue, List.of());
    }
  }

  /** Ends the stream that holds the events, if any: the window is gone. */
  public void close() {
    synchronized (this.lock) {
      if (this.current != null) {
        this.current.add(RETIRED);
        this.current = null;
      }
      this.pending.clear();
    }
  }

  /**
   * Gives the events that {@code queue} didn't deliver, {@code undelivered} first, to whatever
   * holds the stream now, ahead of what it has, or back to the pending line.
   */
  private void requeue(BlockingDeque<byte[]> queue, List<byte[]> undelivered) {
    synchronized (this.lock) {
      if (this.current == queue) {
        this.current = null;
      }
      List<byte[]> left = new ArrayList<>(undelivered);
      queue.drainTo(left);
      left.removeIf(frame -> frame == RETIRED);
      Deque<byte[]> target = this.current != null ? this.current : this.pending;
      for (int i = left.size() - 1; i >= 0; i--) {
        target.addFirst(left.get(i));
      }
    }
  }

  /** Adds what else waits in {@code queue} to {@code batch}, up to a retirement or the limit. */
  private static void drainBatch(BlockingDeque<byte[]> queue, List<byte[]> batch) {
    int size = batch.getFirst().length;
    while (batch.getLast() != RETIRED && size < BATCH_LIMIT) {
      byte[] next = queue.poll();
      if (next == null) {
        return;
      }
      batch.add(next);
      size += next.length;
    }
  }

  private static byte[] join(List<byte[]> frames) {
    if (frames.size() == 1) {
      return frames.getFirst();
    }
    ByteArrayOutputStream joined = new ByteArrayOutputStream();
    frames.forEach(joined::writeBytes);
    return joined.toByteArray();
  }

  private static byte[] frame(String name, String payload, boolean typed) {
    byte[] text =
        ((typed ? "1" : "0") + BridgeProtocol.SEPARATOR + name + BridgeProtocol.SEPARATOR + payload)
            .getBytes(StandardCharsets.UTF_8);
    byte[] frame = new byte[4 + text.length];
    frame[0] = (byte) (text.length >>> 24);
    frame[1] = (byte) (text.length >>> 16);
    frame[2] = (byte) (text.length >>> 8);
    frame[3] = (byte) text.length;
    System.arraycopy(text, 0, frame, 4, text.length);
    return frame;
  }
}
