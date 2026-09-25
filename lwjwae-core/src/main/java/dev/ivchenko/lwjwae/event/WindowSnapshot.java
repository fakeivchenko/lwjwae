package dev.ivchenko.lwjwae.event;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.WindowSize;
import java.util.ArrayList;
import java.util.List;

/**
 * What a window looked like at one moment, as far as its events go.
 *
 * @param size The size of the content area.
 * @param position The position of the frame.
 * @param minimized Whether the window was minimized.
 * @param maximized Whether the window was maximized.
 * @param fullscreen Whether the window was in full screen.
 * @param focused Whether the window had the keyboard focus.
 */
record WindowSnapshot(
    WindowSize size,
    WindowPosition position,
    boolean minimized,
    boolean maximized,
    boolean fullscreen,
    boolean focused) {
  /** Reads the window. Call on its UI thread, where every getter answers at once. */
  static WindowSnapshot of(Window window) {
    return new WindowSnapshot(
        window.size(),
        window.position(),
        window.isMinimized(),
        window.isMaximized(),
        window.isFullscreen(),
        window.isFocused());
  }

  /**
   * The events that lead from {@code before} to this snapshot: the state first, then the size and
   * the place, then the focus, the order in which a user sees them happen.
   */
  List<WindowEventType> changesSince(WindowSnapshot before) {
    List<WindowEventType> changes = new ArrayList<>();
    if (this.minimized != before.minimized) {
      changes.add(this.minimized ? WindowEventType.MINIMIZED : WindowEventType.UNMINIMIZED);
    }
    if (this.maximized != before.maximized) {
      changes.add(this.maximized ? WindowEventType.MAXIMIZED : WindowEventType.UNMAXIMIZED);
    }
    if (this.fullscreen != before.fullscreen) {
      changes.add(
          this.fullscreen ? WindowEventType.FULLSCREEN_ENTERED : WindowEventType.FULLSCREEN_EXITED);
    }
    if (!this.size.equals(before.size)) {
      changes.add(WindowEventType.RESIZED);
    }
    if (!this.position.equals(before.position)) {
      changes.add(WindowEventType.MOVED);
    }
    if (this.focused != before.focused) {
      changes.add(this.focused ? WindowEventType.FOCUSED : WindowEventType.BLURRED);
    }
    return changes;
  }
}
