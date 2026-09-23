package dev.ivchenko.lwjwae.event;

import dev.ivchenko.lwjwae.Window;

/**
 * An event that a Java listener receives, from the page or from Java.
 *
 * @param name The event name, as given to {@code emit}.
 * @param id A number that counts deliveries to the listeners of the window or of the application,
 *     starting at one.
 * @param payload The payload as text. For a typed event, the text that the codec produced; for an
 *     untyped one, the string that was emitted, or an empty string for no payload.
 * @param typed Whether {@code payload} went through the codec and needs decoding.
 * @param window The window that the event came through: the page that emitted it, or the window
 *     that {@code emit} was called on. {@code null} for an event that {@code Application.emit}
 *     delivers to the listeners of the application.
 */
public record Event(String name, long id, String payload, boolean typed, Window window) {}
