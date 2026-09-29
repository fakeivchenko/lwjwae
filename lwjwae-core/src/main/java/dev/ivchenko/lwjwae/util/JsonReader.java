package dev.ivchenko.lwjwae.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads JSON from a string, one character at a time, for {@link JsonUtil#parse}. */
final class JsonReader {
  private final String text;
  private int position;

  JsonReader(String text) {
    this.text = text;
  }

  Object value() {
    this.skipSpace();
    if (this.position >= this.text.length()) {
      throw this.error("a value");
    }
    char character = this.text.charAt(this.position);
    return switch (character) {
      case '{' -> this.object();
      case '[' -> this.array();
      case '"' -> this.string();
      case 't' -> this.literal("true", Boolean.TRUE);
      case 'f' -> this.literal("false", Boolean.FALSE);
      case 'n' -> this.literal("null", null);
      default -> this.number();
    };
  }

  void end() {
    this.skipSpace();
    if (this.position != this.text.length()) {
      throw this.error("the end");
    }
  }

  private Map<String, Object> object() {
    Map<String, Object> object = new LinkedHashMap<>();
    this.position++;
    this.skipSpace();
    if (this.peek() == '}') {
      this.position++;
      return object;
    }
    while (true) {
      this.skipSpace();
      if (this.peek() != '"') {
        throw this.error("a key");
      }
      String key = this.string();
      this.skipSpace();
      this.expect(':');
      object.put(key, this.value());
      this.skipSpace();
      char next = this.peek();
      this.position++;
      if (next == '}') {
        return object;
      }
      if (next != ',') {
        throw this.error("',' or '}'");
      }
    }
  }

  private List<Object> array() {
    List<Object> array = new ArrayList<>();
    this.position++;
    this.skipSpace();
    if (this.peek() == ']') {
      this.position++;
      return array;
    }
    while (true) {
      array.add(this.value());
      this.skipSpace();
      char next = this.peek();
      this.position++;
      if (next == ']') {
        return array;
      }
      if (next != ',') {
        throw this.error("',' or ']'");
      }
    }
  }

  private String string() {
    this.position++;
    StringBuilder out = new StringBuilder();
    while (true) {
      if (this.position >= this.text.length()) {
        throw this.error("the end of the string");
      }
      char character = this.text.charAt(this.position++);
      if (character == '"') {
        return out.toString();
      }
      if (character != '\\') {
        out.append(character);
        continue;
      }
      char escaped = this.peek();
      this.position++;
      switch (escaped) {
        case '"', '\\', '/' -> out.append(escaped);
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'u' -> {
          if (this.position + 4 > this.text.length()) {
            throw this.error("four hex digits");
          }
          try {
            out.append(
                (char) Integer.parseInt(this.text.substring(this.position, this.position + 4), 16));
          } catch (NumberFormatException _) {
            throw this.error("four hex digits");
          }
          this.position += 4;
        }
        default -> throw this.error("an escape");
      }
    }
  }

  private Object literal(String word, Object value) {
    if (!this.text.startsWith(word, this.position)) {
      throw this.error(word);
    }
    this.position += word.length();
    return value;
  }

  private Object number() {
    int start = this.position;
    boolean integral = true;
    while (this.position < this.text.length()) {
      char character = this.text.charAt(this.position);
      if (character == '.' || character == 'e' || character == 'E') {
        integral = false;
      } else if (!(character == '-' || character == '+' || Character.isDigit(character))) {
        break;
      }
      this.position++;
    }
    String digits = this.text.substring(start, this.position);
    try {
      if (integral) {
        BigInteger number = new BigInteger(digits);
        return number.bitLength() < 64 ? (Object) number.longValue() : number.doubleValue();
      }
      return Double.parseDouble(digits);
    } catch (NumberFormatException _) {
      this.position = start;
      throw this.error("a value");
    }
  }

  private void expect(char character) {
    if (this.peek() != character) {
      throw this.error("'" + character + "'");
    }
    this.position++;
  }

  private char peek() {
    if (this.position >= this.text.length()) {
      throw this.error("more");
    }
    return this.text.charAt(this.position);
  }

  private void skipSpace() {
    while (this.position < this.text.length()
        && Character.isWhitespace(this.text.charAt(this.position))) {
      this.position++;
    }
  }

  private IllegalArgumentException error(String expected) {
    return new IllegalArgumentException("Not JSON: expected " + expected + " at " + this.position);
  }
}
