package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.WebKit;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * An {@link RpcExchange} over a {@code WKURLSchemeTask}: the RPC transport on macOS.
 *
 * <p>A task may be touched only on the main thread, and not at all once WebKit has stopped it:
 * every message after {@code webView:stopURLSchemeTask:} raises an Objective-C exception, which
 * takes the process down. So every step of the answer is posted to the main thread and checks
 * there, where the stop also runs, whether the task is still live; nothing needs a lock. The task
 * is retained from the start of the call to its end or its stop.
 *
 * <p>The request is read in full on the main thread, in the start callback, while WebKit guarantees
 * the task. Parts of the answer reach the main thread with at most {@value #IN_FLIGHT} waiting:
 * backpressure without a round trip per part.
 */
final class MacRpcExchange implements RpcExchange {
  private static final int IN_FLIGHT = 16;
  private static final Map<Long, MacRpcExchange> RUNNING = new ConcurrentHashMap<>();

  private final MacWindow window;
  private final MemorySegment task;
  private final String method;
  private final String path;
  private final String origin;
  private final String contentType;
  private final byte[] body;
  private final Semaphore inFlight = new Semaphore(IN_FLIGHT);

  private volatile boolean stopped;
  private volatile Runnable onCancel;

  private MacRpcExchange(MacWindow window, MemorySegment task) {
    this.window = window;
    this.task = Foundation.retain(task);
    this.method = WebKit.taskMethod(task);
    this.path = WebKit.taskPath(task);
    this.origin = WebKit.taskHeader(task, "Origin");
    this.contentType = WebKit.taskHeader(task, "Content-Type");
    this.body = WebKit.taskBody(task);
  }

  /** Starts a call for {@code task}; runs on the main thread, in the start callback. */
  static void start(MacWindow window, MemorySegment task) {
    MacRpcExchange exchange = new MacRpcExchange(window, task);
    RUNNING.put(task.address(), exchange);
    window.rpc(exchange);
  }

  /**
   * Marks the call of {@code task} stopped, if there is one; runs on the main thread, in the stop
   * callback.
   */
  static void stop(MemorySegment task) {
    MacRpcExchange exchange = RUNNING.remove(task.address());
    if (exchange == null) return;
    exchange.stopped = true;
    Foundation.release(exchange.task);
    Runnable action = exchange.onCancel;
    if (action != null) {
      action.run();
    }
  }

  @Override
  public String method() {
    return this.method;
  }

  @Override
  public String path() {
    return this.path;
  }

  @Override
  public String header(String name) {
    if ("Origin".equalsIgnoreCase(name)) {
      return this.origin;
    }
    return "Content-Type".equalsIgnoreCase(name) ? this.contentType : null;
  }

  @Override
  public byte[] body() {
    return this.body;
  }

  @Override
  public void respond(int status, Map<String, String> headers) {
    this.enqueue(() -> WebKit.taskRespond(this.task, status, headers));
  }

  @Override
  public boolean write(byte[] part) {
    if (this.stopped) {
      return false;
    }
    this.enqueue(() -> WebKit.taskData(this.task, part));
    return !this.stopped;
  }

  @Override
  public void end() {
    this.enqueue(
        () -> {
          WebKit.taskFinish(this.task);
          RUNNING.remove(this.task.address());
          Foundation.release(this.task);
          this.stopped = true;
        });
  }

  @Override
  public void onCancel(Runnable action) {
    this.onCancel = action;
    if (this.stopped) {
      action.run();
    }
  }

  /** Runs {@code step} on the main thread unless the task is stopped by then. */
  private void enqueue(Runnable step) {
    this.inFlight.acquireUninterruptibly();
    this.window.postToMain(
        () -> {
          try {
            if (!this.stopped) {
              step.run();
            }
          } finally {
            this.inFlight.release();
          }
        });
  }
}
