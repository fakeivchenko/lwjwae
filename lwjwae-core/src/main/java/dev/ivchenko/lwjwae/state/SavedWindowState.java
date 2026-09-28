package dev.ivchenko.lwjwae.state;

/**
 * What a window remembers from one run of the application to the next.
 *
 * @param width The width of the content area while the window was neither maximized nor in full
 *     screen.
 * @param height The height of the content area, likewise.
 * @param x The position of the frame, likewise, or {@code null} where the platform never told.
 * @param y See {@code x}.
 * @param maximized Whether the window was maximized when it closed.
 */
public record SavedWindowState(int width, int height, Integer x, Integer y, boolean maximized) {
  /** Whether the state carries a position to open the window at. */
  public boolean hasPosition() {
    return this.x != null && this.y != null;
  }
}
