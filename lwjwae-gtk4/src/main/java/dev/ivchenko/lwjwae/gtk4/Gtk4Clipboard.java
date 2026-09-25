package dev.ivchenko.lwjwae.gtk4;

import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.gtk4.binding.Gtk;
import dev.ivchenko.lwjwae.gtk4.binding.Signatures;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The clipboard of GDK 4, the one of the default display.
 *
 * <p>GDK 4 reads only asynchronously: {@code gdk_clipboard_read_text_async} answers through a
 * callback on the GTK thread once the application that owns the clipboard handed the text over,
 * which completes the future of the read. A clipboard without text, or one that Wayland keeps from
 * an application without the focus, finishes with an error, which is no text.
 */
public class Gtk4Clipboard implements Clipboard {
  private static final CallbackRegistry<CompletableFuture<Optional<String>>> READS =
      new CallbackRegistry<>();
  private static final MemorySegment ON_TEXT_READ =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          Gtk4Clipboard.class,
          "onTextRead",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.G_ASYNC_READY_CALLBACK);

  private final UiDispatcher dispatcher;

  Gtk4Clipboard(UiDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public CompletableFuture<Optional<String>> readText() {
    CompletableFuture<Optional<String>> read = new CompletableFuture<>();
    long id = READS.register(read);
    this.dispatcher.post(
        () -> {
          try {
            Gtk.clipboardReadTextAsync(
                Gtk.clipboard(), ON_TEXT_READ, CallbackRegistry.userData(id));
          } catch (Throwable t) {
            READS.unregister(id);
            read.completeExceptionally(t);
          }
        });
    return read;
  }

  @Override
  public void writeText(String text) {
    Objects.requireNonNull(text, "text");
    this.dispatcher.run(() -> Gtk.clipboardSetText(Gtk.clipboard(), text));
  }

  /**
   * The {@code GAsyncReadyCallback} of a read.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onTextRead(
      MemorySegment source, MemorySegment result, MemorySegment userData) {
    CompletableFuture<Optional<String>> read = READS.unregister(userData);
    if (read == null) {
      return;
    }
    try {
      read.complete(Optional.ofNullable(Gtk.clipboardReadTextFinish(source, result)));
    } catch (Throwable t) {
      read.completeExceptionally(t);
    }
  }
}
