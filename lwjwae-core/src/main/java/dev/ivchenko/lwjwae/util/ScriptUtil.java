package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;

/** Builds JavaScript fragments that are safe to pass to an engine for evaluation. */
@UtilityClass
public class ScriptUtil {
  /**
   * Renders {@code value} as a JavaScript string literal, including the quotes.
   *
   * <p>Everything that crosses the bridge goes through this method. A value produced by application
   * code ends up inside a script that the engine executes, so an unescaped quote isn't a formatting
   * bug but an injection. Control characters and the line and paragraph separators (U+2028 and
   * U+2029) are escaped too: the separators are legal inside a Java string, but a syntax error
   * inside a JavaScript string literal before ES2019.
   */
  public String quote(String value) {
    if (value == null) {
      return "null";
    }

    StringBuilder quoted = new StringBuilder(value.length() + 16).append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> quoted.append("\\\"");
        case '\\' -> quoted.append("\\\\");
        case '\n' -> quoted.append("\\n");
        case '\r' -> quoted.append("\\r");
        case '\t' -> quoted.append("\\t");
        default -> {
          if (c < 0x20 || c == '\u2028' || c == '\u2029') {
            quoted.append(String.format("\\u%04x", (int) c));
          } else {
            quoted.append(c);
          }
        }
      }
    }
    return quoted.append('"').toString();
  }

  /**
   * Wraps {@code script} so that its outcome comes back as one tagged string: {@code "S"} plus the
   * {@code String()} form of the value, or {@code "E"} plus the message of the error it threw.
   *
   * <p>WebView2 and WKWebView both lose information on the way back to Java: one reports a thrown
   * exception as a {@code null} result, the other replaces the message with a generic one, and both
   * serialize the value in their own way. Running the text of the caller through {@code (0, eval)}
   * in the global scope inside a {@code try} makes every engine report the same thing, which {@link
   * #completeTagged} decodes.
   */
  public String taggedEvaluation(String script) {
    return """
    (function () {
        try { return "S" + String((0, eval)(%s)); }
        catch (error) { return "E" + (error && error.message !== undefined ? error.message : String(error)); }
    })()\
    """
        .formatted(quote(script));
  }

  /**
   * Completes {@code result} from the string that a {@link #taggedEvaluation} produced: a value, a
   * {@link ScriptEvaluationFailedException}, or {@code "undefined"} when the engine returned
   * nothing.
   */
  public void completeTagged(CompletableFuture<String> result, String tagged) {
    if (tagged == null || tagged.isEmpty()) {
      result.complete("undefined");
    } else if (tagged.charAt(0) == 'E') {
      result.completeExceptionally(new ScriptEvaluationFailedException(tagged.substring(1)));
    } else {
      result.complete(tagged.substring(1));
    }
  }
}
