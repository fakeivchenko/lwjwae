package dev.ivchenko.lwjwae.event;

/**
 * One delivery of a named event to a Java listener, from the page or from Java itself.
 *
 * <p>The payload is text either way. A typed event carries the text that the codec produced, and a
 * typed listener decodes it; an untyped one carries the string that was emitted, as it is.
 *
 * @param name The name that the event was emitted under.
 * @param id A number that counts deliveries in this window, so a listener can tell two deliveries
 *     of the same event apart.
 * @param payload The payload as text. Empty when the event was emitted without one.
 * @param typed Whether {@link #payload()} is the encoding of a value by the codec, as opposed to a
 *     string that was emitted as is.
 */
public record Event(String name, long id, String payload, boolean typed) {}
