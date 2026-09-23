package dev.ivchenko.lwjwae.gtk4.binding;

/**
 * An image as rows of ARGB32 pixels in network byte order, the {@code (iiay)} of a
 * StatusNotifierItem.
 *
 * @param width The width in pixels.
 * @param height The height in pixels.
 * @param argb Four bytes per pixel, alpha first, row after row.
 */
public record Pixmap(int width, int height, byte[] argb) {}
