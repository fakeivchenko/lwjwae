package dev.ivchenko.lwjwae.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Reads and writes JSON as plain Java values: a {@link Map} of {@link String} keys for an object, a
 * {@link List} for an array, {@link String}, {@link Long} or {@link Double} for a number, {@link
 * Boolean}, and {@code null}.
 *
 * <p>The store keeps its documents as JSON and the bridge carries them as JSON, and neither may
 * depend on the codec that an application picked, which may not speak JSON at all. So the core has
 * this small reader and writer of its own. A number without a fraction or an exponent that fits
 * reads as a {@link Long}, any other as a {@link Double}; the keys of an object keep their order.
 */
@UtilityClass
public class JsonUtil {
  /**
   * Reads one JSON value, with nothing but white space around it.
   *
   * @throws IllegalArgumentException If the text isn't JSON.
   */
  public Object parse(String text) {
    JsonReader reader = new JsonReader(text);
    Object value = reader.value();
    reader.end();
    return value;
  }

  /** Writes {@code value} as compact JSON: a {@code NaN} or an infinity writes as {@code null}. */
  public String write(Object value) {
    StringBuilder out = new StringBuilder();
    JsonUtil.write(out, value);
    return out.toString();
  }

  private void write(StringBuilder out, Object value) {
    switch (value) {
      case null -> out.append("null");
      case String text -> JsonUtil.writeString(out, text);
      case Boolean flag -> out.append(flag);
      case Double number when number.isNaN() || number.isInfinite() -> out.append("null");
      case Float number when number.isNaN() || number.isInfinite() -> out.append("null");
      case BigDecimal number -> out.append(number.toPlainString());
      case Number number -> out.append(number);
      case Map<?, ?> map -> {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (!first) {
            out.append(',');
          }
          first = false;
          JsonUtil.writeString(out, String.valueOf(entry.getKey()));
          out.append(':');
          JsonUtil.write(out, entry.getValue());
        }
        out.append('}');
      }
      case Iterable<?> items -> {
        out.append('[');
        boolean first = true;
        for (Object item : items) {
          if (!first) {
            out.append(',');
          }
          first = false;
          JsonUtil.write(out, item);
        }
        out.append(']');
      }
      case Object[] items -> JsonUtil.write(out, List.of(items));
      default -> JsonUtil.writeString(out, value.toString());
    }
  }

  private void writeString(StringBuilder out, String text) {
    out.append('"');
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      switch (character) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        default -> {
          if (character < 0x20 || character == '\u2028' || character == '\u2029') {
            out.append("\\u%04x".formatted((int) character));
          } else {
            out.append(character);
          }
        }
      }
    }
    out.append('"');
  }
}
