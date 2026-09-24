package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.bridge.RpcMessageChannel;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The way back of the RPC message channel on WebView2: {@code PostWebMessageAsString} for a
 * message, and a shared buffer for a large part of an answer, which reaches the page with no
 * encoding at all. Both run on the UI thread of the window, which is where WebView2 accepts them.
 */
final class WindowsMessageChannel implements RpcMessageChannel {
  private final WindowsWindow window;

  WindowsMessageChannel(WindowsWindow window) {
    this.window = window;
  }

  @Override
  public CompletableFuture<?> post(String message) {
    return this.window.postWebMessage(message);
  }

  @Override
  public CompletableFuture<?> postBuffer(
      byte[] data, String additionalDataAsJson, Supplier<String> fallback) {
    return this.window.postSharedBuffer(data, additionalDataAsJson, fallback);
  }
}
