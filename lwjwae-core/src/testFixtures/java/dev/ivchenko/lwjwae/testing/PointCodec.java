package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.bridge.codec.BridgeCodec;

/**
 * A codec that isn't JSON, so the tests prove that the bridge doesn't assume one.
 *
 * <p>A {@link Point} travels as {@code x,y}. The page half recognizes that shape and falls back to
 * JSON for everything else, which keeps the untyped calls of the contract tests, where the page
 * sends an arbitrary object, on the same codec. A backend module registers this class in {@code
 * META-INF/services} of its test classpath, which gives the bridge contract tests a codec without
 * pulling a serialization library into the build.
 */
public class PointCodec implements BridgeCodec {
  private static final String PAGE_SCRIPT =
      """
      {
        encode: (value) => value && typeof value === "object" && "x" in value && "y" in value
            ? value.x + "," + value.y
            : JSON.stringify(value === undefined ? null : value),
        decode: (text) => /^-?\\d+,-?\\d+$/.test(text)
            ? { x: Number(text.split(",")[0]), y: Number(text.split(",")[1]) }
            : JSON.parse(text)
      }\
      """;

  @Override
  public String encode(Object value) {
    if (value == null) {
      return "null";
    }
    Point point = (Point) value;
    return point.x() + "," + point.y();
  }

  @Override
  public <T> T decode(String value, Class<T> type) {
    String[] parts = value.split(",");
    if (parts.length != 2) {
      throw new IllegalArgumentException("Not a point: " + value);
    }
    return type.cast(
        new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())));
  }

  @Override
  public String pageScript() {
    return PAGE_SCRIPT;
  }
}
