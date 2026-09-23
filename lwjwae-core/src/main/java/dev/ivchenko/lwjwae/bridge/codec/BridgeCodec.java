package dev.ivchenko.lwjwae.bridge.codec;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Converts Java objects to text and back for the typed half of the bridge: {@link
 * dev.ivchenko.lwjwae.Window#bind(String, Class, java.util.function.Function)} and {@link
 * dev.ivchenko.lwjwae.Window#emit(String, Object)}.
 *
 * <p>A codec has two halves. The Java half is this interface. The page half is a JavaScript object
 * with the same two operations, which {@link #pageScript()} returns as source text and the bridge
 * runtime evaluates once per document. The two must agree on the format: what {@link #encode}
 * produces, the page decodes, and what the page encodes, {@link #decode} reads. The format is the
 * choice of the codec. JSON is the obvious one, but a codec can carry anything that fits in a
 * string, for example MessagePack in Base64, as long as it ships the matching page half.
 *
 * <p>The core module carries no serialization library of its own. An implementation comes from a
 * codec module on the runtime classpath, found through {@link ServiceLoader}, or from {@link
 * dev.ivchenko.lwjwae.ApplicationParameters#codec()}. An application that already has a configured
 * {@code ObjectMapper} passes that one.
 */
public interface BridgeCodec {
  /**
   * Returns {@code value} as text in the format of this codec. {@code null} becomes the
   * representation of {@code null} in that format.
   */
  String encode(Object value);

  /**
   * Reads {@code value} as {@code type}.
   *
   * @throws IllegalArgumentException If the text isn't in the format of this codec or doesn't fit
   *     the type.
   */
  <T> T decode(String value, Class<T> type);

  /**
   * Returns the page half of this codec: a JavaScript expression that evaluates to an object with
   * {@code encode(value)}, which returns the text of a value, and {@code decode(text)}, which
   * returns the value of a text. The expression runs before the scripts of the document, in every
   * document, so it can't depend on anything that a page loads.
   *
   * <p>A JSON codec returns {@code JSON.stringify} and {@code JSON.parse}, and sends {@code
   * undefined} as {@code null}, because {@code JSON.stringify(undefined)} is no string. The core
   * doesn't provide that text: the format is the choice of the codec, and every codec states its
   * page half itself.
   */
  String pageScript();

  /** Returns the codec on the classpath, if a module that provides one is present. */
  static Optional<BridgeCodec> discover() {
    return ServiceLoader.load(BridgeCodec.class, BridgeCodec.class.getClassLoader()).findFirst();
  }
}
