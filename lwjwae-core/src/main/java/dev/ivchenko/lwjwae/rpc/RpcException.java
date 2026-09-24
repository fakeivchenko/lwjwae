package dev.ivchenko.lwjwae.rpc;

import lombok.Getter;

/**
 * A failure that a handler reports to the page on purpose, with an HTTP status and a code that the
 * page can act on. The page receives it as an error whose {@code status}, {@code code}, and {@code
 * message} are these.
 */
@Getter
public class RpcException extends RuntimeException {
  private final int status;
  private final String code;

  /**
   * @param status The HTTP status, such as {@code 400}.
   * @param code A short, stable code for the page to branch on, such as {@code "not-found"}.
   * @param message What went wrong, for a person.
   */
  public RpcException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  /** The call itself is wrong: {@code 400}. */
  public static RpcException badRequest(String code, String message) {
    return new RpcException(400, code, message);
  }

  /** What the call asks for doesn't exist: {@code 404}. */
  public static RpcException notFound(String code, String message) {
    return new RpcException(404, code, message);
  }

  /** The caller may not make this call: {@code 403}. */
  public static RpcException forbidden(String code, String message) {
    return new RpcException(403, code, message);
  }
}
