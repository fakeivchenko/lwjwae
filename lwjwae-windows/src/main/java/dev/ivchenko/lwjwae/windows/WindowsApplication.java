package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.ComCallback;
import dev.ivchenko.lwjwae.windows.binding.WebView2;
import dev.ivchenko.lwjwae.windows.exception.ComCallFailedException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An application backed by Win32 and WebView2, bound entirely through the Foreign Function and
 * Memory API, without JNI, without {@code WebView2Loader.dll}, and without native artifacts of its
 * own.
 *
 * <p>The application owns the {@code ICoreWebView2Environment}: one browser process, one user data
 * folder, one cookie jar, shared by every {@link WindowsWindow}. WebView2 creates it
 * asynchronously, so the constructor waits through {@link WindowsDispatcher#await} until the
 * environment exists, and a window only has to create its controller. The environment is released
 * when the application closes, after the last window.
 */
public class WindowsApplication extends AbstractApplication {
  private static final Duration CREATION_TIMEOUT = Duration.ofMinutes(1);

  private final AtomicReference<MemorySegment> environment = new AtomicReference<>();

  /** Creates an application with {@link ApplicationParameters#createDefault()}. */
  public WindowsApplication() {
    this(ApplicationParameters.createDefault());
  }

  /**
   * Starts the UI thread if it isn't running and creates the WebView2 environment. Opens no window.
   *
   * @throws IllegalStateException If WebView2 doesn't answer within a minute.
   * @throws ComCallFailedException If WebView2 refuses to create the environment.
   */
  public WindowsApplication(ApplicationParameters parameters) {
    super(WindowsDispatcher.instance(), parameters);
    // Not this.dispatcher(): a call on this from the constructor lets a subclass see it half-built.
    WindowsDispatcher dispatcher = WindowsDispatcher.instance();
    CompletableFuture<MemorySegment> ready = new CompletableFuture<>();
    dispatcher.run(
        () -> {
          MemorySegment handler =
              ComCallback.completion(
                  WebView2.IID_ENVIRONMENT_COMPLETED,
                  (hresult, created) -> completeEnvironment(ready, hresult, created));
          WebView2.createEnvironment(userDataFolder().toString(), handler);
          Com.release(handler);
        });
    try {
      this.environment.set(dispatcher.await(ready, CREATION_TIMEOUT));
    } catch (Exception e) {
      throw e.getCause() instanceof RuntimeException runtime
          ? runtime
          : new IllegalStateException("WebView2 environment creation failed", e);
    }
  }

  private static void completeEnvironment(
      CompletableFuture<MemorySegment> ready, int hresult, MemorySegment created) {
    if (hresult < 0) {
      ready.completeExceptionally(
          new ComCallFailedException("WebView2 environment creation", hresult));
      return;
    }
    Com.addRef(created);
    ready.complete(created);
  }

  /** The shared {@code ICoreWebView2Environment}, owned by this application. */
  MemorySegment environment() {
    MemorySegment current = this.environment.get();
    if (current == null) {
      throw new IllegalStateException("The application is closed");
    }
    return current;
  }

  @Override
  public String engine() {
    return this.dispatcher().call(() -> "WebView2 " + WebView2.browserVersion(this.environment()));
  }

  @Override
  protected AbstractWindow createWindow(long id, WindowParameters parameters) {
    return new WindowsWindow(this, id, parameters);
  }

  @Override
  protected void onClose() {
    MemorySegment closing = this.environment.getAndSet(null);
    if (closing != null) {
      this.dispatcher().run(() -> Com.release(closing));
    }
  }

  private static Path userDataFolder() {
    String local = System.getenv("LOCALAPPDATA");
    Path folder =
        Path.of(local != null ? local : System.getProperty("java.io.tmpdir"), "lwjwae", "WebView2");
    try {
      Files.createDirectories(folder);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create the WebView2 user data folder " + folder, e);
    }
    return folder;
  }
}
