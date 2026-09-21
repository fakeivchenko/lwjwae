package dev.ivchenko.lwjwae.bridge;

import dev.ivchenko.lwjwae.util.ScriptUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import lombok.experimental.UtilityClass;

/**
 * The JavaScript half of the bridge between Java and the page, and the wire format that both halves
 * agree on.
 *
 * <p>The protocol is deliberately platform neutral. Every supported engine can inject a script
 * before the document loads and hand a string back to the host, but each engine names that channel
 * differently: WebKitGTK and WKWebView use {@code window.webkit.messageHandlers.NAME.postMessage},
 * and WebView2 uses {@code window.chrome.webview.postMessage}. Therefore, the backend supplies one
 * expression, which posts a string, and everything above it is shared: the promise bookkeeping and
 * the message framing.
 *
 * <p>A message is {@code id}, {@code name}, and {@code payload}, with {@link #SEPARATOR} between
 * the fields. The separator is a byte that can't occur in a JavaScript identifier and that callers
 * are unlikely to put in a payload, which keeps the framing free of a JSON dependency on either
 * side.
 */
@UtilityClass
public class BridgeProtocol {
  /** The name of the message channel, and of the global that holds the page-side runtime. */
  public final String CHANNEL = "__lwjwaeBridge";

  /**
   * The global that the page uses to listen for events: {@code window.lwjwae.on(name, listener)}.
   */
  public final String PAGE_API = "lwjwae";

  /**
   * The prefix of the {@code CustomEvent} that {@link #emitScript} dispatches on {@code window}.
   */
  public final String EVENT_PREFIX = "lwjwae:";

  /**
   * The field separator inside a bridge message.
   *
   * <p>This is the ASCII unit separator, not NUL. The message reaches the host as a C string, so a
   * NUL byte would truncate it at the first field.
   */
  public final String SEPARATOR = "\u001f";

  private final String BOOTSTRAP_TEMPLATE;

  static {
    String bootstrapTemplateName = "bootstrap.js";
    try (InputStream stream = BridgeProtocol.class.getResourceAsStream(bootstrapTemplateName)) {
      if (stream == null) {
        throw new IllegalStateException("Missing bridge script: " + bootstrapTemplateName);
      }
      BOOTSTRAP_TEMPLATE = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read bridge script: " + bootstrapTemplateName, e);
    }
  }

  /**
   * Splits a raw message into its fields.
   *
   * @return The message, or {@code null} if the text has fewer than three fields or the ID isn't a
   *     number.
   */
  public BridgeMessage parse(String message) {
    String[] parts = message.split(SEPARATOR, 3);
    if (parts.length != 3) {
      return null;
    }
    try {
      return new BridgeMessage(Long.parseLong(parts[0]), parts[1], parts[2]);
    } catch (NumberFormatException _) {
      return null;
    }
  }

  /**
   * Returns the runtime that's injected into every document before the document runs its own
   * scripts. The script is read once from {@code bootstrap.js} next to this class; only the
   * placeholders are filled per window.
   *
   * @param postMessage A JavaScript expression that evaluates to a function that takes one string
   *     and delivers it to the host.
   * @param pageCodec A JavaScript expression that evaluates to the page half of the codec, see
   *     {@link dev.ivchenko.lwjwae.bridge.codec.BridgeCodec#pageScript()}, or {@code "null"} when
   *     the window has no codec.
   */
  public String bootstrapScript(String postMessage, String pageCodec) {
    return BOOTSTRAP_TEMPLATE
        .replace("${channel}", CHANNEL)
        .replace("${pageApi}", PAGE_API)
        .replace("${eventPrefix}", EVENT_PREFIX)
        .replace("${separator}", "\u001f")
        .replace("${post}", postMessage)
        .replace("${codec}", pageCodec);
  }

  /**
   * Exposes one bound name to the page as {@code window.NAME(payload)}, which returns a promise. A
   * string payload is passed through unchanged. Anything else is encoded by the page half of the
   * codec, or rendered with {@code String()} when the window has no codec, so the handler still
   * receives text.
   */
  public String bindingScript(String name) {
    return bindingScript(name, false);
  }

  /**
   * The same as {@link #bindingScript(String)}, or the typed form when {@code typed} is set. In the
   * typed form, the argument is always encoded, regardless of its type, and the answer is decoded
   * before the promise resolves, so the page works with values and the codec with text.
   */
  public String bindingScript(String name, boolean typed) {
    return "window[%s] = (payload) => window.%s.call(%s, payload, %s);"
        .formatted(ScriptUtil.quote(name), CHANNEL, ScriptUtil.quote(name), typed);
  }

  /**
   * Delivers an event to the page: to every listener registered with {@code window.lwjwae.on(name,
   * listener)}, and as a {@code CustomEvent} named {@code lwjwae:NAME} on {@code window}. When
   * {@code typed} is set, the payload is decoded first, so listeners receive the value instead of
   * its text.
   */
  public String emitScript(String name, String payload, boolean typed) {
    return "window.%s.emit(%s, %s, %s);"
        .formatted(CHANNEL, ScriptUtil.quote(name), ScriptUtil.quote(payload), typed);
  }

  /** Completes the page-side promise {@code id} with {@code value}. */
  public String resolveScript(long id, String value) {
    return "window.%s.settle(%d, %s, null);".formatted(CHANNEL, id, ScriptUtil.quote(value));
  }

  /** Fails the page-side promise {@code id} with {@code message}. */
  public String rejectScript(long id, String message) {
    return "window.%s.settle(%d, null, %s);"
        .formatted(CHANNEL, id, ScriptUtil.quote(message == null ? "Handler failed" : message));
  }
}
