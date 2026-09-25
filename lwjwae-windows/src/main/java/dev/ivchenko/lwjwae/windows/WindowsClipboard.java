package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.windows.binding.Kernel32;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The clipboard of Windows, in {@code CF_UNICODETEXT}.
 *
 * <p>Every access opens the clipboard, which one window at a time may have open, and closes it
 * again, on the UI thread. The message window of the dispatcher owns what the application writes: a
 * clipboard opened without a window takes nothing. What was written stays after the application
 * exits, since Windows keeps the data itself.
 */
public class WindowsClipboard implements Clipboard {
  private final WindowsDispatcher dispatcher;

  WindowsClipboard(WindowsDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public CompletableFuture<Optional<String>> readText() {
    CompletableFuture<Optional<String>> read = new CompletableFuture<>();
    this.dispatcher.post(
        () -> {
          try {
            read.complete(this.readOnUiThread());
          } catch (Throwable t) {
            read.completeExceptionally(t);
          }
        });
    return read;
  }

  @Override
  public void writeText(String text) {
    Objects.requireNonNull(text, "text");
    this.dispatcher.run(
        () -> {
          this.open();
          try {
            MemorySegment memory = Kernel32.globalText(text);
            if (!User32.setClipboardData(User32.CF_UNICODETEXT, memory)) {
              Kernel32.globalFree(memory);
              throw new IllegalStateException("SetClipboardData failed: " + Kernel32.lastError());
            }
          } finally {
            User32.closeClipboard();
          }
        });
  }

  private Optional<String> readOnUiThread() {
    this.open();
    try {
      MemorySegment memory = User32.clipboardData(User32.CF_UNICODETEXT);
      return memory.equals(MemorySegment.NULL)
          ? Optional.empty()
          : Optional.ofNullable(Kernel32.readGlobalText(memory));
    } finally {
      User32.closeClipboard();
    }
  }

  /** Opens the clipboard for the message window, or throws when another application holds it. */
  private void open() {
    if (!User32.openClipboard(this.dispatcher.messageWindow())) {
      throw new IllegalStateException("Another application holds the clipboard");
    }
  }
}
