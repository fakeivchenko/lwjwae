package dev.ivchenko.lwjwae.windows.binding;

/**
 * How much of the frame of {@code WS_OVERLAPPEDWINDOW} a window shows around its client area.
 *
 * <p>The style stays whole either way, so snapping, the animations of minimize and maximize, and
 * the resizing that the page starts keep working; {@link User32#removeFrame} answers {@code
 * WM_NCCALCSIZE} for the two that take something away.
 */
public enum WindowFrame {
  /** The title bar and the resize edges, as Windows draws them. */
  FULL,

  /**
   * The resize edges on the left, the right, and at the bottom, with the shadow, and no title bar.
   */
  NO_TITLE_BAR,

  /** Nothing: the client area is the whole window, without a border or a shadow. */
  NONE
}
