package dev.ivchenko.lwjwae;

/**
 * Where the frame of a window is on the screen, measured from the top left in the units of the
 * platform: pixels on Windows and X11, points on macOS.
 *
 * @param x The horizontal distance from the left edge of the screen.
 * @param y The vertical distance from the top edge of the screen.
 */
public record WindowPosition(int x, int y) {}
