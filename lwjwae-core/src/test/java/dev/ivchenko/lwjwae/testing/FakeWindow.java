package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.bridge.BridgeProtocol;
import dev.ivchenko.lwjwae.bridge.RpcMessageChannel;
import dev.ivchenko.lwjwae.event.LoadEvent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * An {@link AbstractWindow} that records what the base class asks of it. Every script that the base
 * class evaluates or injects, every RPC message that it posts to the page, and every URL that it
 * navigates to, is kept for assertions. A test plays the page through {@link #call}.
 */
public class FakeWindow extends AbstractWindow {
  private static final String SEP = BridgeProtocol.SEPARATOR;
  private static final String DOC = "doc";
  private static final Pattern TOKEN = Pattern.compile("const token = trusted \\? \"([^\"]+)\"");

  public final List<String> injected = new CopyOnWriteArrayList<>();
  public final List<String> evaluated = new CopyOnWriteArrayList<>();
  public final List<String> navigated = new CopyOnWriteArrayList<>();
  public final List<String> posted = new CopyOnWriteArrayList<>();

  private String title;
  private int left;
  private int top;
  private int width;
  private int height;
  private boolean resizable = true;
  private boolean devToolsEnabled;

  /** Whether {@link #show()} was called. */
  @Getter private boolean shown;

  private final ReentrantLock evaluations = new ReentrantLock();
  private final Condition evaluatedScript = this.evaluations.newCondition();

  FakeWindow(AbstractApplication application, long id, WindowParameters parameters) {
    super(application, id);
    this.title = parameters.title();
    this.width = parameters.width();
    this.height = parameters.height();
    this.installBridge();
  }

  /** Exposes the protected hook, so that tests can play the part of the engine. */
  public void receive(String message) {
    this.handleBridgeMessage(message);
  }

  /** The token of the window, as the bootstrap hands it to a trusted document. */
  public String token() {
    Matcher matcher = TOKEN.matcher(this.injected.getFirst());
    if (!matcher.find()) {
      throw new AssertionError("The bootstrap carries no token");
    }
    return matcher.group(1);
  }

  /** Plays the page: calls {@code name} with a text body over the message channel. */
  public void call(long id, String name, String body) {
    this.call(id, name, "text/plain;charset=utf-8", body);
  }

  /** Plays the page: calls {@code name} with a body of {@code contentType}. */
  public void call(long id, String name, String contentType, String body) {
    this.receive(
        String.join(
            SEP, "\u0001rpc", this.token(), DOC, Long.toString(id), name, contentType, "s", body));
  }

  /** Plays the page: abandons the call {@code id}. */
  public void cancel(long id) {
    this.receive(String.join(SEP, "\u0001rpc-cancel", this.token(), DOC, Long.toString(id)));
  }

  /** Waits up to five seconds for the whole answer of the call {@code id}. */
  public RpcReply awaitReply(long id) throws InterruptedException {
    String prefix = "\u0001rpc" + SEP + DOC + SEP + id + SEP + "r" + SEP;
    String message = this.awaitPosted(prefix, 1).getFirst();
    String[] fields = message.substring(prefix.length()).split(SEP, 4);
    String body =
        fields[2].equals("b")
            ? new String(Base64.getDecoder().decode(fields[3]), StandardCharsets.UTF_8)
            : fields[3];
    return new RpcReply(Integer.parseInt(fields[0]), fields[1], body);
  }

  /**
   * Waits up to five seconds for {@code count} events in the answer of the call {@code id}, which
   * the test opened under {@link BridgeProtocol#EVENTS_CALL}, and returns them as {@code
   * typed␟name␟payload}.
   */
  public List<String> awaitEvents(long id, int count) throws InterruptedException {
    String prefix = "\u0001rpc" + SEP + DOC + SEP + id + SEP + "d" + SEP;
    long remaining = TimeUnit.SECONDS.toNanos(5);
    this.evaluations.lock();
    try {
      while (true) {
        List<String> events = new ArrayList<>();
        for (String message : this.posted) {
          if (message.startsWith(prefix)) {
            String data = message.substring(prefix.length()).split(SEP, 2)[1];
            ByteBuffer frames = ByteBuffer.wrap(Base64.getDecoder().decode(data));
            while (frames.remaining() >= 4) {
              byte[] frame = new byte[frames.getInt()];
              frames.get(frame);
              events.add(new String(frame, StandardCharsets.UTF_8));
            }
          }
        }
        if (events.size() >= count) {
          return events;
        }
        if (remaining <= 0) {
          throw new AssertionError("Expected " + count + " events but saw " + events);
        }
        remaining = this.evaluatedScript.awaitNanos(remaining);
      }
    } finally {
      this.evaluations.unlock();
    }
  }

  /** Waits up to five seconds for the end of the streamed answer of the call {@code id}. */
  public void awaitEnd(long id) throws InterruptedException {
    this.awaitPosted("\u0001rpc" + SEP + DOC + SEP + id + SEP + "e" + SEP, 1);
  }

  /** Waits up to five seconds for {@code count} posted messages that start with {@code prefix}. */
  private List<String> awaitPosted(String prefix, int count) throws InterruptedException {
    long remaining = TimeUnit.SECONDS.toNanos(5);
    this.evaluations.lock();
    try {
      while (true) {
        List<String> matching =
            this.posted.stream().filter(message -> message.startsWith(prefix)).toList();
        if (matching.size() >= count) {
          return matching;
        }
        if (remaining <= 0) {
          throw new AssertionError("Expected a message " + prefix + " but saw " + this.posted);
        }
        remaining = this.evaluatedScript.awaitNanos(remaining);
      }
    } finally {
      this.evaluations.unlock();
    }
  }

  /** Exposes the protected hook, so that tests can play the part of the engine. */
  public void emit(LoadEvent event) {
    this.emitLoad(event);
  }

  @Override
  protected void injectOnDocumentStart(String script) {
    this.injected.add(script);
  }

  @Override
  protected String bridgeTransportScript() {
    return "(message) => fakeHost.post(message)";
  }

  /** Records what the base class posts to the page instead of evaluating it. */
  @Override
  protected RpcMessageChannel rpcMessageChannel() {
    return this::record;
  }

  private CompletableFuture<?> record(String message) {
    this.evaluations.lock();
    try {
      this.posted.add(message);
      this.evaluatedScript.signalAll();
    } finally {
      this.evaluations.unlock();
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<String> eval(String script) {
    this.evaluations.lock();
    try {
      this.evaluated.add(script);
      this.evaluatedScript.signalAll();
    } finally {
      this.evaluations.unlock();
    }
    return CompletableFuture.completedFuture("undefined");
  }

  @Override
  public void navigate(String url) {
    this.navigated.add(url);
  }

  @Override
  public String url() {
    return this.navigated.isEmpty() ? null : this.navigated.getLast();
  }

  @Override
  public void html(String html) {
    this.navigated.add("about:blank");
  }

  @Override
  public String title() {
    return this.title;
  }

  @Override
  public void title(String title) {
    this.title = title;
  }

  @Override
  public int width() {
    return this.width;
  }

  @Override
  public int height() {
    return this.height;
  }

  @Override
  public void size(int width, int height) {
    this.width = width;
    this.height = height;
  }

  @Override
  public WindowPosition position() {
    return new WindowPosition(this.left, this.top);
  }

  @Override
  public void position(int x, int y) {
    this.left = x;
    this.top = y;
  }

  @Override
  public void center() {
    this.left = 0;
    this.top = 0;
  }

  @Override
  public boolean isResizable() {
    return this.resizable;
  }

  @Override
  public void resizable(boolean resizable) {
    this.resizable = resizable;
  }

  @Override
  public boolean isDevToolsEnabled() {
    return this.devToolsEnabled;
  }

  @Override
  public void devToolsEnabled(boolean devToolsEnabled) {
    this.devToolsEnabled = devToolsEnabled;
  }

  @Override
  public void show() {
    this.shown = true;
  }

  @Override
  public void hide() {
    this.shown = false;
  }

  @Override
  public boolean isVisible() {
    return this.shown;
  }

  /** Plays the part of the close button: hides or closes, as the close action says. */
  @Override
  public void requestClose() {
    if (this.hidesOnCloseRequest()) {
      this.hide();
    } else {
      this.close();
    }
  }

  @Override
  public void close() {
    if (!this.isClosed()) {
      this.markClosed();
    }
  }
}
