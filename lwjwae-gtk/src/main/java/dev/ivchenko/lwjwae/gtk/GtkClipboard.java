package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.glib.binding.GdkPixbuf;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The clipboard of GTK 3, the one that Ctrl+C fills.
 *
 * <p>A read is {@code gtk_clipboard_wait_for_text} on the GTK thread, which runs the main loop
 * until the application that owns the clipboard handed the text over, so the windows keep working
 * meanwhile. It's posted rather than awaited, and completes the future of the read. An image goes
 * as a {@code GdkPixbuf}, which GTK offers in every format that gdk-pixbuf writes, and comes back
 * as one, encoded as PNG.
 */
public class GtkClipboard implements Clipboard {
  private final UiDispatcher dispatcher;

  GtkClipboard(UiDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public CompletableFuture<Optional<String>> readText() {
    CompletableFuture<Optional<String>> read = new CompletableFuture<>();
    this.dispatcher.post(
        () -> {
          try {
            read.complete(Optional.ofNullable(Gtk.clipboardWaitForText()));
          } catch (Throwable t) {
            read.completeExceptionally(t);
          }
        });
    return read;
  }

  @Override
  public void writeText(String text) {
    Objects.requireNonNull(text, "text");
    this.dispatcher.run(() -> Gtk.clipboardSetText(text));
  }

  @Override
  public CompletableFuture<Optional<byte[]>> readImage() {
    CompletableFuture<Optional<byte[]>> read = new CompletableFuture<>();
    this.dispatcher.post(
        () -> {
          try {
            MemorySegment pixbuf = Gtk.clipboardWaitForImage();
            if (pixbuf.equals(MemorySegment.NULL)) {
              read.complete(Optional.empty());
              return;
            }
            try {
              read.complete(Optional.of(GdkPixbuf.encodePng(pixbuf)));
            } finally {
              Glib.unref(pixbuf);
            }
          } catch (Throwable t) {
            read.completeExceptionally(t);
          }
        });
    return read;
  }

  @Override
  public void writeImage(byte[] png) {
    Objects.requireNonNull(png, "png");
    MemorySegment pixbuf = GdkPixbuf.decode(png);
    try {
      this.dispatcher.run(() -> Gtk.clipboardSetImage(pixbuf));
    } finally {
      Glib.unref(pixbuf);
    }
  }
}
