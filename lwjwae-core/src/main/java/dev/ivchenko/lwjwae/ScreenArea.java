package dev.ivchenko.lwjwae;

/**
 * A rectangle on the screens, in the units of {@link WindowPosition}: from the top left of the
 * primary screen, so a screen left of it or above it has negative coordinates.
 *
 * @param x The left edge.
 * @param y The top edge.
 * @param width The width.
 * @param height The height.
 */
public record ScreenArea(int x, int y, int width, int height) {
  /** The top left corner. */
  public WindowPosition position() {
    return new WindowPosition(this.x, this.y);
  }

  /** The width and the height. */
  public WindowSize size() {
    return new WindowSize(this.width, this.height);
  }

  /** Whether the point {@code x, y} is inside. */
  public boolean contains(int x, int y) {
    return x >= this.x && y >= this.y && x < this.x + this.width && y < this.y + this.height;
  }

  /** The part that this area and {@code other} have in common, empty when there is none. */
  public ScreenArea intersection(ScreenArea other) {
    int left = Math.max(this.x, other.x);
    int top = Math.max(this.y, other.y);
    int right = Math.min(this.x + this.width, other.x + other.width);
    int bottom = Math.min(this.y + this.height, other.y + other.height);
    return new ScreenArea(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
  }
}
