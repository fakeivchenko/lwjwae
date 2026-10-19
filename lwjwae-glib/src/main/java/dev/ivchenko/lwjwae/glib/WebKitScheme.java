package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.exception.ResourceNotFoundException;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.Signatures;
import dev.ivchenko.lwjwae.glib.binding.WebKitGtk;
import dev.ivchenko.lwjwae.rpc.RpcExchange;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.MimeTypeUtil;
import dev.ivchenko.lwjwae.util.ResourceUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import lombok.experimental.UtilityClass;

/**
 * The scheme of the application in WebKitGTK, the same on GTK 3 and GTK 4: the files of the
 * application, and the RPC calls of the pages of its windows.
 *
 * <p>The scheme is registered on the default web context of the process, so one handler answers for
 * every web view. A call goes to the window of the web view that made it; anything else is a file
 * of the application from the classpath, a small one in one piece and a large one streamed.
 */
@UtilityClass
public class WebKitScheme {
  private final String ERROR_DOMAIN = "lwjwae";

  private final MemorySegment ON_REQUEST =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          WebKitScheme.class,
          "onRequest",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.VOID_POINTER_POINTER);

  /** The binding of the WebKitGTK that the process loaded, set by {@link #register}. */
  private volatile WebKitGtk webKit;

  /** The GTK thread, on which a streamed answer finishes its request. */
  private volatile UiDispatcher dispatcher;

  /**
   * Serves the scheme of the application on the default web context, once a process. Call on the
   * GTK thread.
   */
  public void register(WebKitGtk binding, UiDispatcher uiThread) {
    webKit = binding;
    dispatcher = uiThread;
    binding.registerUriScheme(ResourceUtil.SCHEME, ON_REQUEST);
  }

  /**
   * Serves one request of the scheme: a call of a page to its window, or a file of the classpath.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private void onRequest(MemorySegment request, MemorySegment userData) {
    WebKitGtk binding = webKit;
    String path = "";
    try {
      String requested = binding.uriSchemeRequestPath(request);
      if (requested != null && requested.startsWith(RpcExchange.PATH_PREFIX)) {
        WebKitGtkWindow window =
            WebKitGtkWindow.ofWebView(binding.uriSchemeRequestWebView(request));
        if (window != null) {
          window.rpc(new WebKitRpcExchange(binding, dispatcher, request, requested));
          return;
        }
      }
      path = ResourceUtil.servedPath(requested == null ? "" : requested);
      long size = ResourceUtil.servedSize(path);
      if (size < 0 || size >= ResourceUtil.STREAM_THRESHOLD) {
        ResourceUtil.stream(new WebKitRpcExchange(binding, dispatcher, request, path), path);
        return;
      }
      byte[] content = ResourceUtil.read(path);
      MemorySegment stream = Glib.memoryInputStream(Glib.copyToNative(content), content.length);
      try {
        binding.uriSchemeRequestFinish(request, stream, content.length, MimeTypeUtil.of(path));
      } finally {
        Glib.unref(stream);
      }
    } catch (ResourceNotFoundException e) {
      binding.uriSchemeRequestFinishError(request, Glib.error(ERROR_DOMAIN, 404, e.getMessage()));
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      binding.uriSchemeRequestFinishError(
          request, Glib.error(ERROR_DOMAIN, 500, "Could not serve " + path));
    }
  }
}
