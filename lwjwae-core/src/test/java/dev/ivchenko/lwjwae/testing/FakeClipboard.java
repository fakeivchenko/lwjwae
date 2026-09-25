package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.clipboard.Clipboard;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** A {@link Clipboard} without a desktop: it keeps what was written. */
public class FakeClipboard implements Clipboard {
  private volatile String text;

  @Override
  public CompletableFuture<Optional<String>> readText() {
    return CompletableFuture.completedFuture(Optional.ofNullable(this.text));
  }

  @Override
  public void writeText(String text) {
    this.text = text;
  }
}
