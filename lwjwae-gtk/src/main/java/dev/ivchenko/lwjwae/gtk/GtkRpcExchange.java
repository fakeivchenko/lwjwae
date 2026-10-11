package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.glib.WebKitRpcExchange;
import dev.ivchenko.lwjwae.gtk.binding.WebKit;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import java.lang.foreign.MemorySegment;
import java.util.Map;

/** A {@link WebKitRpcExchange} through the WebKitGTK of this backend. */
final class GtkRpcExchange extends WebKitRpcExchange {
  /** Takes a reference to {@code request}; runs on the GTK thread, in the scheme handler. */
  GtkRpcExchange(UiDispatcher dispatcher, MemorySegment request, String path) {
    super(
        dispatcher,
        request,
        path,
        WebKit.uriSchemeRequestMethod(request),
        WebKit.uriSchemeRequestHeader(request, "Origin"),
        WebKit.uriSchemeRequestHeader(request, TOKEN_HEADER),
        WebKit.uriSchemeRequestHeader(request, "Content-Type"),
        WebKit.uriSchemeRequestBody(request));
  }

  @Override
  protected void finish(
      MemorySegment request, MemorySegment stream, int status, Map<String, String> headers) {
    WebKit.uriSchemeRequestFinishWithStream(request, stream, status, headers);
  }
}
