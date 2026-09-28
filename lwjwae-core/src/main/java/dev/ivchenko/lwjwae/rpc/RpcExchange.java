package dev.ivchenko.lwjwae.rpc;

import java.util.Map;

/**
 * One RPC request as a backend received it from its engine, and the way back: the part of an RPC
 * call that differs per engine. A backend implements it and hands it to {@code
 * AbstractWindow#serveRpc}.
 *
 * <p>The request looks like HTTP whatever the transport: a method, a path under {@link
 * #PATH_PREFIX}, headers, and a body. The answer may be written in parts, from any thread, long
 * after the request arrived; {@link #write} hands one part to the engine as soon as it's written.
 */
public interface RpcExchange {
  /** Where RPC names live on the resource origin of a window: {@code /__lwjwae/rpc/NAME}. */
  String PATH_PREFIX = "/__lwjwae/rpc/";

  /** The HTTP method: {@code POST} for a call, {@code OPTIONS} for a CORS preflight. */
  String method();

  /** The path, such as {@code /__lwjwae/rpc/files.read}. */
  String path();

  /** A request header, or {@code null}; names compare without regard to case. */
  String header(String name);

  /** The body of the request; empty when there is none. */
  byte[] body();

  /** Starts the response. Called once, before any {@link #write}. */
  void respond(int status, Map<String, String> headers);

  /**
   * Hands {@code part} to the page.
   *
   * @return False if the page abandoned the request before the part left, so it never reaches the
   *     page; a part that left is reported as written, even if the page gives up right after.
   */
  boolean write(byte[] part);

  /** Ends the response. */
  void end();

  /**
   * Answers in one step: {@link #respond}, {@link #write} unless {@code body} is empty, and {@link
   * #end}. A transport that pays per message overrides it to send the whole answer as one.
   */
  default void reply(int status, Map<String, String> headers, byte[] body) {
    this.respond(status, headers);
    if (body.length > 0) {
      this.write(body);
    }
    this.end();
  }

  /**
   * Registers what to run when the page abandons the request. A backend whose engine reports it
   * calls the action at once; one whose engine doesn't, lets {@link #write} report it instead.
   */
  void onCancel(Runnable action);
}
