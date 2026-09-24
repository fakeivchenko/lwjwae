package dev.ivchenko.lwjwae;

import lombok.Builder;

/**
 * What a window starts with.
 *
 * <p>Every component has a usable default, which the canonical constructor applies, so a caller
 * only states what it needs:
 *
 * <pre>{@code
 * WindowParameters.builder().title("Docs").width(1280).height(800).build()
 * }</pre>
 *
 * @param title The window title. Default: {@code "Application"}.
 * @param width The initial window width, in pixels. A non-positive value means the default.
 *     Default: {@code 1024}.
 * @param height The initial window height, in pixels. A non-positive value means the default.
 *     Default: {@code 768}.
 * @param x The screen X coordinate that the window opens at, in the units of the platform, or
 *     {@code null} to let the window manager choose. Both {@code x} and {@code y} must be set for
 *     either to count. Wayland ignores them: a client can't place its window there.
 * @param y The screen Y coordinate that the window opens at, measured from the top. See {@code x}.
 * @param centered Whether the window opens in the middle of the screen. Wins over {@code x} and
 *     {@code y}. Default: {@code false}, except on macOS, where every window opens centered.
 * @param url The URL to load after the window exists, or {@code null} to leave the window blank.
 *     This is a convenience for simple cases. {@link Application#open} navigates before it returns,
 *     so to observe a load from its first event, leave this value unset, register the listener, and
 *     call {@link Window#navigate} yourself.
 * @param resource The classpath resource to load after the window exists, the way {@link
 *     Window#loadResource} does, or {@code null}. Wins over {@code url}.
 * @param closeAction What the window does when the user closes it. Default: {@link
 *     CloseAction#CLOSE}. {@link Window#closeAction(CloseAction)} changes it later.
 * @param minimumSize The smallest size of the content area, see {@link Window#minimumSize(int,
 *     int)}. Default: {@link WindowSize#NONE}.
 * @param maximumSize The largest size of the content area, see {@link Window#maximumSize(int,
 *     int)}. Default: {@link WindowSize#NONE}.
 * @param alwaysOnTop Whether the window stays above other windows, see {@link
 *     Window#alwaysOnTop(boolean)}. Default: {@code false}.
 */
@Builder(toBuilder = true)
public record WindowParameters(
    String title,
    int width,
    int height,
    Integer x,
    Integer y,
    boolean centered,
    String url,
    String resource,
    CloseAction closeAction,
    WindowSize minimumSize,
    WindowSize maximumSize,
    boolean alwaysOnTop) {
  private static final String DEFAULT_TITLE = "Application";
  private static final int DEFAULT_WIDTH = 1024;
  private static final int DEFAULT_HEIGHT = 768;

  public WindowParameters {
    if (closeAction == null) {
      closeAction = CloseAction.CLOSE;
    }
    if (minimumSize == null) {
      minimumSize = WindowSize.NONE;
    }
    if (maximumSize == null) {
      maximumSize = WindowSize.NONE;
    }
    if (title == null || title.isBlank()) {
      title = DEFAULT_TITLE;
    }
    if (width <= 0) {
      width = DEFAULT_WIDTH;
    }
    if (height <= 0) {
      height = DEFAULT_HEIGHT;
    }
    if (x == null || y == null) {
      x = null;
      y = null;
    }
    if (url != null && url.isBlank()) {
      url = null;
    }
    if (resource != null && resource.isBlank()) {
      resource = null;
    }
  }

  /**
   * Whether the window opens at {@link #x()}, {@link #y()} rather than where the platform puts it.
   */
  public boolean hasPosition() {
    return this.x != null;
  }

  /**
   * Creates a window with every default: 1024 by 768 pixels, blank, and titled {@code
   * "Application"}.
   */
  public static WindowParameters createDefault() {
    return builder().build();
  }
}
