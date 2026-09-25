package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.windows.binding.Gdiplus;
import dev.ivchenko.lwjwae.windows.binding.Kernel32;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The clipboard of Windows: text in {@code CF_UNICODETEXT}, images as PNG and as a bitmap.
 *
 * <p>Every access opens the clipboard, which one window at a time may have open, and closes it
 * again, on the UI thread. The message window of the dispatcher owns what the application writes: a
 * clipboard opened without a window takes nothing. What was written stays after the application
 * exits, since Windows keeps the data itself.
 */
public class WindowsClipboard implements Clipboard {
  /** The registered format that browsers and Office write images in, with their alpha. */
  private static final String PNG_FORMAT = "PNG";

  private static final int PNG_SIGNATURE_LENGTH = 8;

  /** The length, the type, and the CRC around the data of a chunk. */
  private static final int CHUNK_OVERHEAD = 12;

  /** The type of the last chunk of a PNG, {@code IEND}, as a big-endian number. */
  private static final int IEND = 0x49454E44;

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
            User32.emptyClipboard();
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

  @Override
  public CompletableFuture<Optional<byte[]>> readImage() {
    CompletableFuture<Optional<byte[]>> read = new CompletableFuture<>();
    this.dispatcher.post(
        () -> {
          try {
            read.complete(this.readImageOnUiThread());
          } catch (Throwable t) {
            read.completeExceptionally(t);
          }
        });
    return read;
  }

  @Override
  public void writeImage(byte[] png) {
    Objects.requireNonNull(png, "png");
    this.dispatcher.run(
        () -> {
          MemorySegment bitmap = Gdiplus.hbitmapFromPng(png);
          this.open();
          try {
            User32.emptyClipboard();
            MemorySegment memory = Kernel32.globalBytes(png);
            if (!User32.setClipboardData(User32.registerClipboardFormat(PNG_FORMAT), memory)) {
              Kernel32.globalFree(memory);
            }
            if (!User32.setClipboardData(User32.CF_BITMAP, bitmap)) {
              throw new IllegalStateException("SetClipboardData failed: " + Kernel32.lastError());
            }
          } finally {
            User32.closeClipboard();
          }
        });
  }

  /** The {@code PNG} format if another application wrote one, which keeps alpha, or the bitmap. */
  private Optional<byte[]> readImageOnUiThread() {
    this.open();
    try {
      MemorySegment png = User32.clipboardData(User32.registerClipboardFormat(PNG_FORMAT));
      if (!png.equals(MemorySegment.NULL)) {
        byte[] data = Kernel32.readGlobal(png);
        return Optional.of(Arrays.copyOf(data, WindowsClipboard.pngLength(data)));
      }
      MemorySegment bitmap = User32.clipboardData(User32.CF_BITMAP);
      if (bitmap.equals(MemorySegment.NULL)) {
        return Optional.empty();
      }
      byte[] data = Gdiplus.pngFromHbitmap(bitmap);
      return Optional.of(Arrays.copyOf(data, WindowsClipboard.pngLength(data)));
    } finally {
      User32.closeClipboard();
    }
  }

  /**
   * The length of the PNG at the start of {@code data}, up to the end of its {@code IEND} chunk:
   * global memory is allocated in larger steps than what was written into it, and the rest is left
   * over. All of {@code data} when it doesn't parse as PNG.
   */
  private static int pngLength(byte[] data) {
    ByteBuffer buffer = ByteBuffer.wrap(data);
    int offset = PNG_SIGNATURE_LENGTH;
    while (offset + CHUNK_OVERHEAD <= data.length) {
      int length = buffer.getInt(offset);
      int type = buffer.getInt(offset + 4);
      if (length < 0) {
        break;
      }
      offset += CHUNK_OVERHEAD + length;
      if (type == IEND) {
        return Math.min(offset, data.length);
      }
    }
    return data.length;
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
