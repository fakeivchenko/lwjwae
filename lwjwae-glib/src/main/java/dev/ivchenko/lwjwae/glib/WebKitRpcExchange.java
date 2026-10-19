package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.Unix;
import dev.ivchenko.lwjwae.glib.binding.WebKitGtk;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import java.lang.foreign.MemorySegment;
import java.util.Map;

/**
 * An {@link RpcExchange} on a {@code WebKitURISchemeRequest}: a {@code fetch} of the page to the
 * scheme of the application, the same on GTK 3 and GTK 4.
 *
 * <p>The answer goes back through a pipe. WebKit reads a {@code GUnixInputStream} on the read end
 * as data arrives, Java writes the other end, and a write blocks while WebKit is behind, which is
 * the backpressure of the stream. A page that abandons the call makes WebKit drop the stream, which
 * closes the read end, and the next write fails with {@code EPIPE}: that is how the handler learns
 * of it. WebKit doesn't report the cancellation any sooner, so {@link #onCancel} never fires.
 */
final class WebKitRpcExchange implements RpcExchange {
  private final WebKitGtk webKit;
  private final UiDispatcher dispatcher;
  private final MemorySegment request;
  private final String method;
  private final String path;
  private final String origin;
  private final String token;
  private final String contentType;
  private final MemorySegment bodyStream;

  private byte[] body;
  private int writeFd = -1;

  /**
   * Takes a reference to {@code request}; runs on the GTK thread, in the scheme handler.
   *
   * @param webKit The binding of the WebKitGTK that the process loaded.
   * @param path The path of the request, or of the resource that a large file streams.
   */
  WebKitRpcExchange(WebKitGtk webKit, UiDispatcher dispatcher, MemorySegment request, String path) {
    this.webKit = webKit;
    this.dispatcher = dispatcher;
    this.request = request;
    Glib.ref(request);
    String requestMethod = webKit.uriSchemeRequestMethod(request);
    this.method = requestMethod == null ? "GET" : requestMethod;
    this.path = path;
    this.origin = webKit.uriSchemeRequestHeader(request, "Origin");
    this.token = webKit.uriSchemeRequestHeader(request, TOKEN_HEADER);
    this.contentType = webKit.uriSchemeRequestHeader(request, "Content-Type");
    // The GInputStream of the body, which this exchange owns, or NULL without one.
    this.bodyStream = webKit.uriSchemeRequestBody(request);
  }

  @Override
  public String method() {
    return this.method;
  }

  @Override
  public String path() {
    return this.path;
  }

  @Override
  public String header(String name) {
    if ("Origin".equalsIgnoreCase(name)) {
      return this.origin;
    }
    if (TOKEN_HEADER.equalsIgnoreCase(name)) {
      return this.token;
    }
    return "Content-Type".equalsIgnoreCase(name) ? this.contentType : null;
  }

  /** Reads the body on the calling thread, the first time: never the GTK thread. */
  @Override
  public synchronized byte[] body() {
    if (this.body == null) {
      if (this.bodyStream.equals(MemorySegment.NULL)) {
        this.body = new byte[0];
      } else {
        this.body = Unix.readAll(this.bodyStream);
        Glib.unref(this.bodyStream);
      }
    }
    return this.body;
  }

  @Override
  public void respond(int status, Map<String, String> headers) {
    int[] pipe = Unix.pipe();
    this.writeFd = pipe[1];
    this.dispatcher.run(
        () -> {
          MemorySegment stream = Unix.inputStream(pipe[0]);
          this.webKit.uriSchemeRequestFinishWithStream(this.request, stream, status, headers);
          Glib.unref(stream);
          Glib.unref(this.request);
        });
  }

  @Override
  public boolean write(byte[] part) {
    return Unix.writeAll(this.writeFd, part);
  }

  @Override
  public void end() {
    Unix.close(this.writeFd);
  }

  @Override
  public void onCancel(Runnable action) {}
}
