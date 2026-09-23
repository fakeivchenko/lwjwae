package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import java.util.List;
import java.util.function.Consumer;

/** A {@link Tray} without a desktop: it only tells its application when it closes. */
public class FakeTray implements Tray {
  private final Consumer<Tray> closedCallback;

  private volatile boolean closed;

  FakeTray(Consumer<Tray> closedCallback) {
    this.closedCallback = closedCallback;
  }

  @Override
  public void icon(byte[] png) {}

  @Override
  public void tooltip(String tooltip) {}

  @Override
  public void menu(List<TrayMenuItem> menu) {}

  @Override
  public boolean isClosed() {
    return this.closed;
  }

  @Override
  public void close() {
    if (this.closed) {
      return;
    }
    this.closed = true;
    this.closedCallback.accept(this);
  }
}
