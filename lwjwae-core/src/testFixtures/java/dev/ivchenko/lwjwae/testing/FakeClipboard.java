package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.clipboard.Clipboard;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** A {@link Clipboard} without a desktop: it keeps what was written. */
public class FakeClipboard implements Clipboard {
  private volatile String text;
  private volatile byte[] image;

  @Override
  public CompletableFuture<Optional<String>> readText() {
    return CompletableFuture.completedFuture(Optional.ofNullable(this.text));
  }

  @Override
  public void writeText(String text) {
    this.text = text;
    this.image = null;
  }

  @Override
  public CompletableFuture<Optional<byte[]>> readImage() {
    return CompletableFuture.completedFuture(Optional.ofNullable(this.image));
  }

  @Override
  public void writeImage(byte[] png) {
    this.image = png.clone();
    this.text = null;
  }
}
