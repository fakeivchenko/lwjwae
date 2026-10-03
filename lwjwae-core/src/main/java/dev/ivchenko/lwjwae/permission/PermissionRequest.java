package dev.ivchenko.lwjwae.permission;

import java.util.Objects;

/**
 * One permission that a page asks for. A request for the camera and the microphone together, which
 * one {@code getUserMedia} call can make, reaches the handler as two requests, and the page gets
 * both permissions only if the handler grants both.
 *
 * @param kind What the page asks for.
 * @param origin The origin of the page that asks, such as {@code https://example.com}, or an empty
 *     string where the engine doesn't tell.
 */
public record PermissionRequest(PermissionKind kind, String origin) {
  public PermissionRequest {
    Objects.requireNonNull(kind, "kind");
    if (origin == null) {
      origin = "";
    }
  }
}
