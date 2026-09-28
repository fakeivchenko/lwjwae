package dev.ivchenko.lwjwae.state;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowSize;
import dev.ivchenko.lwjwae.event.WindowEvent;
import dev.ivchenko.lwjwae.event.WindowEventType;

/**
 * Follows one window from its events and keeps what it will remember: the size and the place it had
 * while it was neither maximized, minimized, nor in full screen, and whether it's maximized. That
 * is what a user expects back: a window that closed maximized opens maximized, and shrinks to the
 * size it had before when it's restored.
 */
public final class WindowStateTracker {
  private final String key;
  private int width;
  private int height;
  private Integer left;
  private Integer top;
  private boolean maximized;
  private boolean minimized;
  private boolean fullscreen;

  /**
   * Starts from the window as it opened, or as a saved state opened it.
   *
   * @param key The state key of the window.
   * @param saved The state that the window opened with, or {@code null}.
   */
  public WindowStateTracker(String key, Window window, SavedWindowState saved) {
    this.key = key;
    WindowSize size = saved != null ? new WindowSize(saved.width(), saved.height()) : window.size();
    this.width = size.width();
    this.height = size.height();
    if (saved != null && saved.hasPosition()) {
      this.left = saved.x();
      this.top = saved.y();
    }
    this.maximized = saved != null && saved.maximized();
  }

  /** Takes in one event of the window. */
  public synchronized void update(WindowEvent event) {
    switch (event.type()) {
      case MAXIMIZED -> this.maximized = true;
      case UNMAXIMIZED -> this.maximized = false;
      case MINIMIZED -> this.minimized = true;
      case UNMINIMIZED -> this.minimized = false;
      case FULLSCREEN_ENTERED -> this.fullscreen = true;
      case FULLSCREEN_EXITED -> this.fullscreen = false;
      default -> {
        // A change of size or place is taken below, and the focus isn't part of the state.
      }
    }
    if (this.maximized || this.minimized || this.fullscreen) {
      return;
    }
    if (event.type() == WindowEventType.RESIZED) {
      this.width = event.size().width();
      this.height = event.size().height();
    }
    if (event.type() == WindowEventType.MOVED) {
      this.left = event.position().x();
      this.top = event.position().y();
    }
  }

  /** The state key of the window. */
  public String key() {
    return this.key;
  }

  /** What the window remembers now. */
  public synchronized SavedWindowState state() {
    return new SavedWindowState(this.width, this.height, this.left, this.top, this.maximized);
  }
}
