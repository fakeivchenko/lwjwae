package dev.ivchenko.lwjwae.rpc;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.util.MimeTypeUtil;
import dev.ivchenko.lwjwae.util.ResourceUtil;

/**
 * One call from a page: the body that it sent, and the way to answer it.
 *
 * <p>The body is bytes. {@link #text()} reads them as UTF-8, and {@link #value(Class)} decodes them
 * with the codec of the application, which is what the page sends with {@code lwjwae.invoke}. An
 * answer is either whole, through one of the {@code reply} methods, or streamed, through {@link
 * #stream(String)}, which the page reads as it's written; a call is answered once.
 */
public interface RpcCall {
  /** The name that the page called. */
  String name();

  /** The window whose page made the call. */
  Window window();

  /** The body of the call; empty when the page sent none. */
  byte[] body();

  /** The media type that the page gave the body, such as {@code application/json}, or null. */
  String contentType();

  /** The body as UTF-8 text. */
  String text();

  /**
   * The body decoded by the codec of the application: the value that the page passed to {@code
   * lwjwae.invoke}, or an object that it passed to {@code lwjwae.call}.
   *
   * @throws IllegalStateException If the application has no codec.
   */
  <T> T value(Class<T> type);

  /** Answers with {@code body}, of the media type {@code contentType}. */
  void reply(byte[] body, String contentType);

  /** Answers with {@code text}, as {@code text/plain} in UTF-8. */
  void reply(String text);

  /**
   * Answers with a file among the resources of the application, such as {@code "app/report.pdf"},
   * of the media type that its extension names.
   *
   * @throws ResourceNotFoundException If the classpath has no such resource.
   */
  default void replyResource(String resource) {
    this.reply(ResourceUtil.read(resource), MimeTypeUtil.of(resource));
  }

  /**
   * Answers with {@code value} encoded by the codec of the application: what {@code lwjwae.invoke}
   * resolves to on the page, decoded by the page half of the codec.
   *
   * @throws IllegalStateException If the application has no codec.
   */
  void replyValue(Object value);

  /**
   * Starts an answer that the page reads as it arrives, of the media type {@code contentType}.
   * Closing the stream ends the answer; a handler that returns ends it too.
   */
  RpcStream stream(String contentType);

  /** Whether the page abandoned the call, for example with an {@code AbortSignal}. */
  boolean isCancelled();
}
