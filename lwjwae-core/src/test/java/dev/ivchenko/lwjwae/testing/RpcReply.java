package dev.ivchenko.lwjwae.testing;

/**
 * A whole answer to a call that a test made through {@link FakeWindow#call}: the {@code r} message
 * of the message channel.
 *
 * @param status The HTTP status.
 * @param contentType The media type, empty when the answer has none.
 * @param body The body, as text.
 */
public record RpcReply(int status, String contentType, String body) {}
