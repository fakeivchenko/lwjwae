package dev.ivchenko.lwjwae.testing;

import java.util.List;

/**
 * A value with nesting, a collection, and text that needs escaping, so a codec round trip proves
 * more than two integers.
 *
 * @param name The label. The tests put quotes, backslashes, and non-Latin text here.
 * @param points The outline.
 */
public record Shape(String name, List<Point> points) {}
