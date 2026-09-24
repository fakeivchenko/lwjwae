package dev.ivchenko.lwjwae;

/**
 * A size of the content area of a window, in the units of {@link Window#width()}. For a limit, zero
 * in either dimension means no limit in that dimension.
 *
 * @param width The width.
 * @param height The height.
 */
public record WindowSize(int width, int height) {
  /** No limit. */
  public static final WindowSize NONE = new WindowSize(0, 0);
}
