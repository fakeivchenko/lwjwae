package dev.ivchenko.lwjwae.bridge;

/**
 * One call from the page, as parsed by {@link BridgeProtocol#parse}.
 *
 * @param id The promise that the page waits on. Answered with {@link BridgeProtocol#resolveScript}
 *     or {@link BridgeProtocol#rejectScript}.
 * @param name The bound name that the page called.
 * @param payload The argument, as text. It can contain the separator.
 */
public record BridgeMessage(long id, String name, String payload) {}
