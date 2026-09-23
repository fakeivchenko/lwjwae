package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.event.LoadEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;

/**
 * An {@link AbstractWindow} that records what the base class asks of it. Every script that the base
 * class evaluates or injects, and every URL that it navigates to, is kept for assertions.
 */
public class FakeWindow extends AbstractWindow {
  public final List<String> injected = new CopyOnWriteArrayList<>();
  public final List<String> evaluated = new CopyOnWriteArrayList<>();
  public final List<String> navigated = new CopyOnWriteArrayList<>();

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

  /** Exposes the protected hook, so that tests can play the part of the engine. */
  public void emit(LoadEvent event) {
    this.emitLoad(event);
  }

  /**
   * Waits up to five seconds for {@code script} to be evaluated. A bound handler replies from its
   * own thread, so the reply arrives some time after the call that caused it.
   *
   * @throws AssertionError If the script isn't evaluated in time.
   */
  public void awaitEvaluation(String script) throws InterruptedException {
    long remaining = TimeUnit.SECONDS.toNanos(5);
    this.evaluations.lock();
    try {
      while (!this.evaluated.contains(script)) {
        if (remaining <= 0) {
          throw new AssertionError(
              "Expected evaluation of " + script + " but saw " + this.evaluated);
        }
        remaining = this.evaluatedScript.awaitNanos(remaining);
      }
    } finally {
      this.evaluations.unlock();
    }
  }

  @Override
  protected void injectOnDocumentStart(String script) {
    this.injected.add(script);
  }

  @Override
  protected String bridgeTransportScript() {
    return "(message) => fakeHost.post(message)";
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
  public void close() {
    if (!this.isClosed()) {
      this.markClosed();
    }
  }
}
