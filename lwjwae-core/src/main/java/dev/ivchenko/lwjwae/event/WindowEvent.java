package dev.ivchenko.lwjwae.event;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.WindowSize;
import java.util.Objects;

/**
 * A change of a window: its size, its place, its state, or its focus.
 *
 * @param type What changed.
 * @param window The window that changed.
 * @param size The size of the content area after the change.
 * @param position The position of the frame after the change, {@code 0, 0} where the platform
 *     doesn't tell.
 */
public record WindowEvent(
    WindowEventType type, Window window, WindowSize size, WindowPosition position) {
  public WindowEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(window, "window");
  }
}
