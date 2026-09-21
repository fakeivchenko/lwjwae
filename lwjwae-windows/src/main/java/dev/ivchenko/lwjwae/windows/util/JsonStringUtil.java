package dev.ivchenko.lwjwae.windows.util;

import lombok.experimental.UtilityClass;

/**
 * Decodes the one JSON shape that WebView2 returns. {@code ExecuteScript} returns its result as
 * JSON, and the backend arranges for that result to always be a string.
 */
@UtilityClass
public class JsonStringUtil {
  /**
   * The value of a JSON string literal, with quotes and escapes resolved. Any other JSON text, such
   * as {@code null}, a number, or an object, is returned unchanged.
   */
  public String decode(String json) {
    if (json == null
        || json.length() < 2
        || json.charAt(0) != '"'
        || json.charAt(json.length() - 1) != '"') {
      return json;
    }
    StringBuilder text = new StringBuilder(json.length());
    for (int i = 1; i < json.length() - 1; i++) {
      char c = json.charAt(i);
      if (c != '\\') {
        text.append(c);
        continue;
      }
      char escaped = json.charAt(++i);
      switch (escaped) {
        case 'n' -> text.append('\n');
        case 'r' -> text.append('\r');
        case 't' -> text.append('\t');
        case 'b' -> text.append('\b');
        case 'f' -> text.append('\f');
        case 'u' -> {
          text.append((char) Integer.parseInt(json, i + 1, i + 5, 16));
          i += 4;
        }
        default -> text.append(escaped);
      }
    }
    return text.toString();
  }
}
