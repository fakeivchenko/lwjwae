package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The general pasteboard of AppKit, the one of Command-C, in plain text.
 *
 * <p>The pasteboard server holds what was written, so it stays after the application exits. A read
 * runs on the main thread, where AppKit wants it, and completes the future there.
 */
public class MacClipboard implements Clipboard {
  private final UiDispatcher dispatcher;

  MacClipboard(UiDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public CompletableFuture<Optional<String>> readText() {
    CompletableFuture<Optional<String>> read = new CompletableFuture<>();
    this.dispatcher.post(
        () -> {
          try {
            read.complete(Optional.ofNullable(AppKit.pasteboardText()));
          } catch (Throwable t) {
            read.completeExceptionally(t);
          }
        });
    return read;
  }

  @Override
  public void writeText(String text) {
    Objects.requireNonNull(text, "text");
    this.dispatcher.run(() -> AppKit.setPasteboardText(text));
  }
}
