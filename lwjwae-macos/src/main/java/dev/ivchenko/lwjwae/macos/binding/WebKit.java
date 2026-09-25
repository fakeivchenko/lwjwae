package dev.ivchenko.lwjwae.macos.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * WebKit: {@code WKWebView}, its configuration, user scripts, script messages, and URL scheme
 * tasks.
 */
@UtilityClass
public class WebKit {
  static {
    // Loaded for the classes it registers; nothing is looked up by symbol.
    SymbolLookup _ = NativeLibraries.load("/System/Library/Frameworks/WebKit.framework/WebKit");
  }

  private final String DEVELOPER_EXTRAS_KEY = "developerExtrasEnabled";
  private final Pattern MACOS_RELEASE = Pattern.compile("(\\d+(?:\\.\\d+)+)");
  private final long INJECT_AT_DOCUMENT_START = 0;
  private final long AUTORESIZE_WIDTH_AND_HEIGHT = (1 << 1) | (1 << 4);

  /** {@code +[WKWebViewConfiguration new]}, owned by the caller. */
  public MemorySegment configuration() {
    return ObjC.send(ObjC.cls("WKWebViewConfiguration"), "new");
  }

  /**
   * Sets {@code developerExtrasEnabled} on the preferences of {@code webView}: the Web Inspector,
   * and the "Inspect Element" entry of the context menu. The key is private but stable since Safari
   * 9, which is what every embedding application uses.
   */
  public void setDeveloperExtrasEnabled(MemorySegment webView, boolean enabled) {
    MemorySegment number = ObjC.send(ObjC.cls("NSNumber"), "numberWithBool:", enabled);
    ObjC.sendVoid(
        preferences(webView), "setValue:forKey:", number, Foundation.string(DEVELOPER_EXTRAS_KEY));
  }

  /** Reads {@code developerExtrasEnabled} from the preferences of {@code webView}. */
  public boolean isDeveloperExtrasEnabled(MemorySegment webView) {
    MemorySegment number =
        ObjC.send(preferences(webView), "valueForKey:", Foundation.string(DEVELOPER_EXTRAS_KEY));
    return !ObjC.isNull(number) && ObjC.sendLong(number, "integerValue") != 0;
  }

  private MemorySegment preferences(MemorySegment webView) {
    return ObjC.send(ObjC.send(webView, "configuration"), "preferences");
  }

  /** Returns the {@code WKUserContentController} of {@code configuration}. Borrowed. */
  public MemorySegment userContentController(MemorySegment configuration) {
    return ObjC.send(configuration, "userContentController");
  }

  /**
   * Routes every {@code scheme://} request of the views built from {@code configuration} to {@code
   * handler}.
   */
  public void setUrlSchemeHandler(
      MemorySegment configuration, MemorySegment handler, String scheme) {
    ObjC.sendVoid(
        configuration, "setURLSchemeHandler:forURLScheme:", handler, Foundation.string(scheme));
  }

  /** A view that fills its window and follows its resizing. The caller owns it. */
  public MemorySegment webView(int width, int height, MemorySegment configuration) {
    MemorySegment webView;
    try (Arena arena = Arena.ofConfined()) {
      webView =
          ObjC.sendWithRect(
              ObjC.send(ObjC.cls("WKWebView"), "alloc"),
              "initWithFrame:configuration:",
              Foundation.rect(arena, 0, 0, width, height),
              configuration);
    }
    ObjC.sendVoid(webView, "setAutoresizingMask:", AUTORESIZE_WIDTH_AND_HEIGHT);
    return webView;
  }

  /** Calls {@code -[WKWebView setNavigationDelegate:]}. {@code NULL} detaches the delegate. */
  public void setNavigationDelegate(MemorySegment webView, MemorySegment delegate) {
    ObjC.sendVoid(webView, "setNavigationDelegate:", delegate);
  }

  /** Calls {@code -[WKWebView setUIDelegate:]}. {@code NULL} detaches the delegate. */
  public void setUiDelegate(MemorySegment webView, MemorySegment delegate) {
    ObjC.sendVoid(webView, "setUIDelegate:", delegate);
  }

  /** The URL that a {@code WKNavigationAction} goes to. */
  public String navigationActionUrl(MemorySegment action) {
    return Foundation.urlString(ObjC.send(ObjC.send(action, "request"), "URL"));
  }

  /**
   * Opens the {@code name} message channel: {@code
   * window.webkit.messageHandlers.NAME.postMessage()}.
   */
  public void addScriptMessageHandler(
      MemorySegment userContentController, MemorySegment handler, String name) {
    ObjC.sendVoid(
        userContentController, "addScriptMessageHandler:name:", handler, Foundation.string(name));
  }

  /**
   * Runs {@code source} in every frame of every later document, before the scripts of the document.
   */
  public void addUserScript(MemorySegment userContentController, String source) {
    MemorySegment script =
        ObjC.send(
            ObjC.send(ObjC.cls("WKUserScript"), "alloc"),
            "initWithSource:injectionTime:forMainFrameOnly:",
            Foundation.string(source),
            INJECT_AT_DOCUMENT_START,
            false);
    ObjC.sendVoid(userContentController, "addUserScript:", script);
    Foundation.release(script);
  }

  /** Calls {@code -[WKWebView loadRequest:]} with a request for {@code url}. */
  public void loadUrl(MemorySegment webView, String url) {
    MemorySegment request =
        ObjC.send(ObjC.cls("NSURLRequest"), "requestWithURL:", Foundation.url(url));
    MemorySegment _ = ObjC.send(webView, "loadRequest:", request);
  }

  /**
   * Calls {@code -[WKWebView loadHTMLString:baseURL:]}: relative links resolve against {@code
   * baseUrl}, and the document has its origin.
   */
  public void loadHtml(MemorySegment webView, String html, String baseUrl) {
    MemorySegment _ =
        ObjC.send(
            webView, "loadHTMLString:baseURL:", Foundation.string(html), Foundation.url(baseUrl));
  }

  /** The current URL, or {@code null} before the first load. */
  public String url(MemorySegment webView) {
    MemorySegment url = ObjC.send(webView, "URL");
    return ObjC.isNull(url) ? null : Foundation.urlString(url);
  }

  /**
   * {@code evaluateJavaScript:completionHandler:}. {@code completion} is a block from {@link
   * ObjC#block}.
   */
  public void evaluateJavaScript(MemorySegment webView, String script, MemorySegment completion) {
    ObjC.sendVoid(
        webView, "evaluateJavaScript:completionHandler:", Foundation.string(script), completion);
  }

  /** The body of a {@code WKScriptMessage} posted as a string. */
  public String messageBody(MemorySegment message) {
    return Foundation.string(ObjC.send(message, "body"));
  }

  /** The URL a {@code WKURLSchemeTask} asks for. */
  public String taskUrl(MemorySegment task) {
    return Foundation.urlString(ObjC.send(ObjC.send(task, "request"), "URL"));
  }

  /** Answers a scheme task with a complete resource. */
  public void finishTask(MemorySegment task, String url, String mimeType, byte[] content) {
    MemorySegment response =
        ObjC.send(
            ObjC.send(ObjC.cls("NSURLResponse"), "alloc"),
            "initWithURL:MIMEType:expectedContentLength:textEncodingName:",
            Foundation.url(url),
            Foundation.string(mimeType),
            content.length,
            MemorySegment.NULL);
    ObjC.sendVoid(task, "didReceiveResponse:", response);
    ObjC.sendVoid(task, "didReceiveData:", Foundation.data(content));
    ObjC.sendVoid(task, "didFinish");
    Foundation.release(response);
  }

  /**
   * Fails a scheme task. WebKit reports it to the navigation delegate as a failed provisional
   * navigation.
   */
  public void failTask(MemorySegment task, String message) {
    ObjC.sendVoid(task, "didFailWithError:", Foundation.error(404, message));
  }

  /**
   * The version of the loaded WebKit framework. Its bundle version is a dotted build number on most
   * releases. Where it's a bare number, the macOS release that ships the framework is the version
   * that people recognize.
   */
  public String version() {
    String build = Foundation.bundleVersion(ObjC.cls("WKWebView"));
    if (build != null && build.contains(".")) {
      return build;
    }
    Matcher release = MACOS_RELEASE.matcher(Foundation.operatingSystemVersion());
    return (build == null ? "0" : build) + " macOS " + (release.find() ? release.group(1) : "0");
  }

  // --- RPC: requests with a method and a body, answered in parts ---

  /** {@code task.request.HTTPMethod}. */
  public String taskMethod(MemorySegment task) {
    return Foundation.string(ObjC.send(ObjC.send(task, "request"), "HTTPMethod"));
  }

  /** {@code task.request.URL.path}. */
  public String taskPath(MemorySegment task) {
    return Foundation.string(ObjC.send(ObjC.send(ObjC.send(task, "request"), "URL"), "path"));
  }

  /** {@code [task.request valueForHTTPHeaderField:name]}, or null. */
  public String taskHeader(MemorySegment task, String name) {
    return Foundation.string(
        ObjC.send(ObjC.send(task, "request"), "valueForHTTPHeaderField:", Foundation.string(name)));
  }

  /** {@code task.request.URL.query}, or an empty string. */
  public String taskQuery(MemorySegment task) {
    String query =
        Foundation.string(ObjC.send(ObjC.send(ObjC.send(task, "request"), "URL"), "query"));
    return query == null ? "" : query;
  }

  /**
   * The body of the request of {@code task}: {@code HTTPBody}, or, where WebKit hands the body over
   * as a stream instead, {@code HTTPBodyStream} read to its end.
   */
  public byte[] taskBody(MemorySegment task) {
    MemorySegment request = ObjC.send(task, "request");
    MemorySegment data = ObjC.send(request, "HTTPBody");
    if (!ObjC.isNull(data)) {
      long length = ObjC.sendLong(data, "length");
      return ObjC.send(data, "bytes").reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
    }
    MemorySegment stream = ObjC.send(request, "HTTPBodyStream");
    if (ObjC.isNull(stream)) {
      return new byte[0];
    }
    java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
    ObjC.sendVoid(stream, "open");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate(65536);
      long read;
      while ((read = ObjC.sendLong(stream, "read:maxLength:", buffer, 65536)) > 0) {
        body.write(buffer.asSlice(0, read).toArray(ValueLayout.JAVA_BYTE), 0, (int) read);
      }
    } finally {
      ObjC.sendVoid(stream, "close");
    }
    return body.toByteArray();
  }

  /** Starts the answer of {@code task}: an {@code NSHTTPURLResponse} with a status and headers. */
  public void taskRespond(MemorySegment task, int status, java.util.Map<String, String> headers) {
    MemorySegment fields = ObjC.send(ObjC.cls("NSMutableDictionary"), "dictionary");
    for (java.util.Map.Entry<String, String> header : headers.entrySet()) {
      ObjC.sendVoid(
          fields,
          "setObject:forKey:",
          Foundation.string(header.getValue()),
          Foundation.string(header.getKey()));
    }
    MemorySegment response =
        ObjC.send(
            ObjC.send(ObjC.cls("NSHTTPURLResponse"), "alloc"),
            "initWithURL:statusCode:HTTPVersion:headerFields:",
            ObjC.send(ObjC.send(task, "request"), "URL"),
            status,
            Foundation.string("HTTP/1.1"),
            fields);
    ObjC.sendVoid(task, "didReceiveResponse:", response);
    Foundation.release(response);
  }

  /** Hands one part of the answer to the page. */
  public void taskData(MemorySegment task, byte[] part) {
    ObjC.sendVoid(task, "didReceiveData:", Foundation.data(part));
  }

  /** Ends the answer of {@code task}. */
  public void taskFinish(MemorySegment task) {
    ObjC.sendVoid(task, "didFinish");
  }
}
