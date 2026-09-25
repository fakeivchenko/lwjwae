package dev.ivchenko.lwjwae.bridge;

import dev.ivchenko.lwjwae.WindowEdge;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.WindowPosition;
import dev.ivchenko.lwjwae.dialog.FileType;
import dev.ivchenko.lwjwae.dialog.MessageButtons;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.MessageLevel;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.event.Event;
import dev.ivchenko.lwjwae.util.ScriptUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The JavaScript half of the bridge between Java and the page, and the names and formats that both
 * halves agree on.
 *
 * <p>The bridge speaks RPC only: a binding, an event from the page, and a window that the page
 * opens are calls, and the events of Java travel in the answer of one call that the page keeps
 * open. Every supported engine can inject a script before the document loads and hand a string back
 * to the host, but each engine names that channel differently: WebKitGTK and WKWebView use {@code
 * window.webkit.messageHandlers.NAME.postMessage}, and WebView2 uses {@code
 * window.chrome.webview.postMessage}. Therefore, the backend supplies one expression, which posts a
 * string, and everything above it is shared.
 *
 * <p>Fields inside a message or a payload are separated by {@link #SEPARATOR}, a byte that can't
 * occur in a JavaScript identifier and that callers are unlikely to put in a payload, which keeps
 * the framing free of a JSON dependency on either side.
 */
@UtilityClass
public class BridgeProtocol {
  /** The name of the message channel, and of the global that holds the page-side runtime. */
  public final String CHANNEL = "__lwjwaeBridge";

  /**
   * The global that holds the page API: {@code window.lwjwae.listen(name, handler)}, {@code once},
   * {@code emit}, {@code open}, and {@code close}.
   */
  public final String PAGE_API = "lwjwae";

  /**
   * The call under which the page delivers its own events to Java. It contains a colon, which no
   * handler name can, so a handler can never shadow it. The body is {@code typed␟name␟payload}.
   */
  public final String EVENT_CALL = "lwjwae:emit";

  /**
   * The call that a document opens at its start and reads for as long as it lives: its answer
   * carries the events of Java. Reserved like {@link #EVENT_CALL}.
   */
  public final String EVENTS_CALL = "lwjwae:events";

  /**
   * The event that carries the changes of the window to the page, as JSON: {@code type}, {@code
   * width}, {@code height}, {@code x}, {@code y}. {@code window.lwjwae.window.listen} hears it.
   */
  public final String WINDOW_EVENT = "lwjwae:window";

  /**
   * The name under which a page asks for a new window, through {@code window.lwjwae.open(options)}.
   * Reserved like {@link #EVENT_CALL}. The payload is what {@link #parseWindowParameters} reads;
   * the promise resolves to the ID of the window.
   */
  public final String OPEN_CALL = "lwjwae:open";

  /**
   * The name under which a page closes its own window, through {@code window.lwjwae.close()}.
   * Reserved like {@link #EVENT_CALL}. The promise never settles: the document is gone first.
   */
  public final String CLOSE_CALL = "lwjwae:close";

  /**
   * The name under which a page controls its own window, through {@code window.lwjwae.window}:
   * minimizes it, maximizes it, moves it from a drag region, and so on. Reserved like {@link
   * #EVENT_CALL}. The body is an action and, for some, an argument after {@link #SEPARATOR}.
   */
  public final String CONTROL_CALL = "lwjwae:control";

  /**
   * The name under which a page shows a dialog over its window, through {@code
   * window.lwjwae.dialog}. Reserved like {@link #EVENT_CALL}. The body is {@code open}, {@code
   * save}, or {@code message}, and the fields of the dialog after {@link #SEPARATOR}.
   */
  public final String DIALOG_CALL = "lwjwae:dialog";

  /**
   * The name under which a page reads and writes the clipboard of the desktop, through {@code
   * window.lwjwae.clipboard}. Reserved like {@link #EVENT_CALL}. The body is {@code read-text}, or
   * {@code write-text} and the text after {@link #SEPARATOR}; a read answers {@code 1} and the
   * text, or {@code 0} when there is none.
   */
  public final String CLIPBOARD_CALL = "lwjwae:clipboard";

  /**
   * The separator between the kinds of file of a dialog in a {@link #DIALOG_CALL}: the ASCII record
   * separator.
   */
  public final String RECORD_SEPARATOR = "\u001e";

  /**
   * The separator between the name and the extensions of one kind of file: the ASCII group
   * separator.
   */
  public final String GROUP_SEPARATOR = "\u001d";

  /**
   * The media type of a value that the codec encoded: what {@code lwjwae.invoke} and a typed
   * binding send, and what {@code RpcCall.replyValue} answers with, so the page half decodes it.
   */
  public final String VALUE_TYPE = "application/x-lwjwae-value; charset=utf-8";

  /** What a bound name must look like. */
  private final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

  /**
   * What an RPC name must look like: it ends up in a URL path, so letters, digits, {@code . _ -}.
   */
  private final Pattern RPC_NAME = Pattern.compile("[A-Za-z0-9_.-]+");

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
   * Returns the runtime that's injected into every document before the document runs its own
   * scripts. The script is read once from {@code bootstrap.js} next to this class; only the
   * placeholders are filled per window.
   *
   * @param postMessage A JavaScript expression that evaluates to a function that takes one string
   *     and delivers it to the host.
   * @param pageCodec A JavaScript expression that evaluates to the page half of the codec, see
   *     {@link dev.ivchenko.lwjwae.bridge.codec.BridgeCodec#pageScript()}, or {@code "null"} when
   *     the window has no codec.
   * @param rpcTransport A JavaScript object that tells the bootstrap how calls reach Java and how
   *     answers come back: {@code {base: URL}} or {@code {base: null, webview2: true}}.
   * @param token The secret of the window that a call message carries.
   * @param trustedOrigins The origins whose documents get the token; a top-level {@code
   *     about:blank}, what {@code Window.html} shows on WebView2, gets it too.
   * @param resizeEdges The edges at which the page offers to resize a window without native resize
   *     edges there, none for most windows.
   */
  public String bootstrapScript(
      String postMessage,
      String pageCodec,
      String rpcTransport,
      String token,
      List<String> trustedOrigins,
      List<WindowEdge> resizeEdges) {
    String origins =
        trustedOrigins.stream().map(ScriptUtil::quote).collect(Collectors.joining(",", "[", "]"));
    String edges =
        resizeEdges.stream()
            .map(WindowEdge::pageName)
            .map(ScriptUtil::quote)
            .collect(Collectors.joining(",", "[", "]"));
    return BOOTSTRAP_TEMPLATE
        .replace("${channel}", CHANNEL)
        .replace("${pageApi}", PAGE_API)
        .replace("${eventCall}", EVENT_CALL)
        .replace("${eventsCall}", EVENTS_CALL)
        .replace("${windowEvent}", WINDOW_EVENT)
        .replace("${openCall}", OPEN_CALL)
        .replace("${closeCall}", CLOSE_CALL)
        .replace("${controlCall}", CONTROL_CALL)
        .replace("${dialogCall}", DIALOG_CALL)
        .replace("${clipboardCall}", CLIPBOARD_CALL)
        .replace("${resizeEdges}", edges)
        .replace("${separator}", "\u001f")
        .replace("${post}", postMessage)
        .replace("${codec}", pageCodec)
        .replace("${rpc}", rpcTransport)
        .replace("${token}", ScriptUtil.quote(token))
        .replace("${trusted}", origins);
  }

  /**
   * Exposes one bound name to the page as {@code window.NAME(payload)}, which returns a promise. A
   * string payload is passed through unchanged. Anything else is encoded by the page half of the
   * codec, or rendered with {@code String()} when the window has no codec, so the handler still
   * receives text.
   */
  public String bindingScript(String name) {
    return BridgeProtocol.bindingScript(name, false);
  }

  /**
   * The same as {@link #bindingScript(String)}, or the typed form when {@code typed} is set. In the
   * typed form, the argument is always encoded, regardless of its type, and the answer is decoded
   * before the promise resolves, so the page works with values and the codec with text.
   */
  public String bindingScript(String name, boolean typed) {
    return "window[%s] = (payload) => window.%s.bound(%s, payload, %s);"
        .formatted(ScriptUtil.quote(name), CHANNEL, ScriptUtil.quote(name), typed);
  }

  /**
   * Splits the body of an {@link #EVENT_CALL} into the event that the page emitted.
   *
   * @return The event with an ID of zero, which the backend replaces, or {@code null} if the text
   *     has fewer than three fields.
   */
  public Event parseEvent(String payload) {
    String[] parts = payload.split(SEPARATOR, 3);
    if (parts.length != 3) {
      return null;
    }
    return new Event(parts[1], 0, parts[2], parts[0].equals("1"), null);
  }

  /**
   * Parses the payload of an {@link #OPEN_CALL}: the title, the size, the position, {@code
   * centered}, the URL, the resource, and the four flags of the frame, separated by {@link
   * #SEPARATOR}, an empty field for one that the page left unset, {@code 1} for a set flag, and
   * {@code 0} for a flag of the frame that the page turned off.
   *
   * @return The parameters, defaults applied, or {@code null} if the text doesn't have the shape or
   *     a number doesn't parse.
   */
  public WindowParameters parseWindowParameters(String payload) {
    String[] parts = payload.split(SEPARATOR, -1);
    if (parts.length != 12) {
      return null;
    }
    try {
      return WindowParameters.builder()
          .title(parts[0])
          .size(BridgeProtocol.integer(parts[1]), BridgeProtocol.integer(parts[2]))
          .position(
              parts[3].isEmpty() || parts[4].isEmpty()
                  ? null
                  : new WindowPosition(Integer.parseInt(parts[3]), Integer.parseInt(parts[4])))
          .centered(parts[5].equals("1"))
          .url(parts[6])
          .resource(parts[7])
          .decorated(BridgeProtocol.flag(parts[8]))
          .closable(BridgeProtocol.flag(parts[9]))
          .minimizable(BridgeProtocol.flag(parts[10]))
          .maximizable(BridgeProtocol.flag(parts[11]))
          .build();
    } catch (NumberFormatException _) {
      return null;
    }
  }

  /**
   * Parses the fields of an open dialog in a {@link #DIALOG_CALL}: the title, the directory, {@code
   * 1} for multiple, {@code 1} for directories, and the kinds of file, see {@link #parseFileTypes}.
   *
   * @return The parameters, or {@code null} if the text doesn't have the shape.
   */
  public OpenDialogParameters parseOpenDialog(String payload) {
    String[] parts = payload.split(SEPARATOR, -1);
    if (parts.length != 5) {
      return null;
    }
    return OpenDialogParameters.builder()
        .title(BridgeProtocol.text(parts[0]))
        .directory(BridgeProtocol.path(parts[1]))
        .multiple(parts[2].equals("1"))
        .directories(parts[3].equals("1"))
        .fileTypes(BridgeProtocol.parseFileTypes(parts[4]))
        .build();
  }

  /**
   * Parses the fields of a save dialog in a {@link #DIALOG_CALL}: the title, the directory, the
   * file name, and the kinds of file.
   *
   * @return The parameters, or {@code null} if the text doesn't have the shape.
   */
  public SaveDialogParameters parseSaveDialog(String payload) {
    String[] parts = payload.split(SEPARATOR, -1);
    if (parts.length != 4) {
      return null;
    }
    return SaveDialogParameters.builder()
        .title(BridgeProtocol.text(parts[0]))
        .directory(BridgeProtocol.path(parts[1]))
        .fileName(BridgeProtocol.text(parts[2]))
        .fileTypes(BridgeProtocol.parseFileTypes(parts[3]))
        .build();
  }

  /**
   * Parses the fields of a message dialog in a {@link #DIALOG_CALL}: the title, the message, the
   * detail, the name of the {@link MessageLevel}, and the name of the {@link MessageButtons}, empty
   * for the default.
   *
   * @return The parameters, or {@code null} if the text doesn't have the shape or names no level or
   *     no buttons.
   */
  public MessageDialogParameters parseMessageDialog(String payload) {
    String[] parts = payload.split(SEPARATOR, -1);
    if (parts.length != 5) {
      return null;
    }
    try {
      return MessageDialogParameters.builder()
          .title(BridgeProtocol.text(parts[0]))
          .message(parts[1])
          .detail(BridgeProtocol.text(parts[2]))
          .level(parts[3].isEmpty() ? null : MessageLevel.valueOf(parts[3]))
          .buttons(parts[4].isEmpty() ? null : MessageButtons.valueOf(parts[4]))
          .build();
    } catch (IllegalArgumentException _) {
      return null;
    }
  }

  /**
   * Parses kinds of file: each one its name and its extensions separated by {@link
   * #GROUP_SEPARATOR}, and the kinds separated by {@link #RECORD_SEPARATOR}.
   */
  public List<FileType> parseFileTypes(String text) {
    if (text.isEmpty()) {
      return List.of();
    }
    return Arrays.stream(text.split(RECORD_SEPARATOR))
        .map(type -> type.split(GROUP_SEPARATOR, -1))
        .map(parts -> new FileType(parts[0], List.of(parts).subList(1, parts.length)))
        .toList();
  }

  private String text(String field) {
    return field.isEmpty() ? null : field;
  }

  private Path path(String field) {
    return field.isEmpty() ? null : Path.of(field);
  }

  /**
   * Checks that a name can be bound on {@code window}.
   *
   * @throws IllegalArgumentException If {@code name} isn't a JavaScript identifier.
   */
  public void checkIdentifier(String name) {
    if (!IDENTIFIER.matcher(name).matches()) {
      throw new IllegalArgumentException("Not a JavaScript identifier: " + name);
    }
  }

  /**
   * Checks that a name can take an RPC handler.
   *
   * @throws IllegalArgumentException If {@code name} has a character other than a letter, a digit,
   *     or {@code . _ -}, or none.
   */
  public void checkRpcName(String name) {
    if (name == null || !RPC_NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Not an RPC name: " + name);
    }
  }

  /** A flag of the frame: {@code null}, the default, unless the page set it. */
  private Boolean flag(String text) {
    return text.isEmpty() ? null : text.equals("1");
  }

  /** A number field, or {@code 0}, which the window parameters read as their default. */
  private int integer(String text) {
    return text.isEmpty() ? 0 : Integer.parseInt(text);
  }
}
