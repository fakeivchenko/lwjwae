# lwjwae-core

The platform-neutral half of lwjwae: the public API, the bridge between Java and the page, backend
discovery, the helpers that every backend binds native code through, and the contract tests that
every backend runs.

The module has no native code and no JSON library. A backend module for your platform supplies
the window; a codec module supplies JSON when you use the typed bridge methods.

## Public API

| Type                                                                                                                                                     | Role                                                                                                                                                                                                                                                                                                                                               |
|----------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`Application`](src/main/java/dev/ivchenko/lwjwae/Application.java)                                                                                      | Entry point. Picks a backend and creates a window.                                                                                                                                                                                                                                                                                                 |
| [`ApplicationBackend`](src/main/java/dev/ivchenko/lwjwae/ApplicationBackend.java)                                                                        | One window with a web view inside. Every backend implements it.                                                                                                                                                                                                                                                                                    |
| [`ApplicationParameters`](src/main/java/dev/ivchenko/lwjwae/ApplicationParameters.java)                                                                  | What the window starts with: title, size, URL, development server, codec.                                                                                                                                                                                                                                                                          |
| [`ApplicationBackendProvider`](src/main/java/dev/ivchenko/lwjwae/ApplicationBackendProvider.java)                                                        | The service that a backend module registers so that [`Application`](src/main/java/dev/ivchenko/lwjwae/Application.java) can find it.                                                                                                                                                                                                               |
| [`bridge.codec.BridgeCodec`](src/main/java/dev/ivchenko/lwjwae/bridge/codec/BridgeCodec.java)                                                            | JSON in and out, for `bind(name, Class, handler)` and `emit(name, Object)`.                                                                                                                                                                                                                                                                        |
| [`event.LoadEvent`](src/main/java/dev/ivchenko/lwjwae/event/LoadEvent.java), [`event.LoadState`](src/main/java/dev/ivchenko/lwjwae/event/LoadState.java) | Page load lifecycle notifications.                                                                                                                                                                                                                                                                                                                 |
| `exception.*`                                                                                                                                            | [`BackendNotAvailableException`](src/main/java/dev/ivchenko/lwjwae/exception/BackendNotAvailableException.java), [`ResourceNotFoundException`](src/main/java/dev/ivchenko/lwjwae/exception/ResourceNotFoundException.java), [`ScriptEvaluationFailedException`](src/main/java/dev/ivchenko/lwjwae/exception/ScriptEvaluationFailedException.java). |

A minimal application:

```java
try (ApplicationBackend application = Application.create(ApplicationParameters.builder()
    .title("Docs")
    .width(1280)
    .height(800)
    .build())) {
  application.bind("reverse", text -> new StringBuilder(text).reverse().toString());
  application.loadResource("app/index.html");
  application.run();
}
```

Every method of `ApplicationBackend` is safe to call from any thread. The backend forwards the
call to its UI thread, and a getter blocks until the UI thread has answered.

## Backend discovery

[`Application.create`](src/main/java/dev/ivchenko/lwjwae/Application.java) finds a backend in three steps:

1. Loads every [`ApplicationBackendProvider`](src/main/java/dev/ivchenko/lwjwae/ApplicationBackendProvider.java) on the classpath through `ServiceLoader`. A backend
   module registers its provider in `META-INF/services/dev.ivchenko.lwjwae.ApplicationBackendProvider`.
2. Drops the providers whose `isSupported()` returns `false`. A provider checks the operating
   system first, then whether its native libraries load. It doesn't initialize a toolkit, because
   every candidate is asked, including the ones that lose.
3. Picks the provider with the highest `priority()`. The `lwjwae.backend` system property or the
   `LWJWAE_BACKEND` environment variable names a provider explicitly instead, for example to try a
   fallback on a machine that also has the native backend. The setting applies only if that
   provider supports the machine.

When no provider survives step 2, `create` throws [`BackendNotAvailableException`](src/main/java/dev/ivchenko/lwjwae/exception/BackendNotAvailableException.java). The message lists
every provider that was found and, from its `unsupportedReason()`, why it stepped aside, so you can
tell a missing dependency from a missing native library without a debugger:

```
No backend supports this machine (Linux).
  gtk3-webkit2gtk-4.1: can't load libwebkit2gtk-4.1.so.0; install them with: sudo dnf install gtk3 webkit2gtk4.1
  win32-webview2: runs on Windows, not on Linux
```

## Bridge

The bridge connects Java and the page in both directions. It's a string protocol with no JSON
dependency in the core, and only its transport differs between engines.

### Installation

A backend calls `installBridge()` after its native view exists and before the first page loads.
This injects `bootstrap.js`, the page-side runtime, into every document before the scripts of the
document run. The runtime defines `window.__lwjwaeBridge` (promise bookkeeping, listeners, framing)
and `window.lwjwae` (`on` and `off`, the page-facing event API).

The runtime needs one thing from the backend: `bridgeTransportScript()`, a JavaScript expression
that evaluates to a function of one string and delivers that string to the host. WebKit backends
return `window.webkit.messageHandlers.NAME.postMessage`; WebView2 returns
`window.chrome.webview.postMessage`.

### From the page to Java: `bind`

1. Java calls `bind("reverse", handler)`. The name must be a JavaScript identifier. The handler goes
   into a map, and `bindingScript("reverse")` is both injected for future documents and evaluated
   in the current one, so binding after the page loaded works too.
2. The page calls `window.reverse("abc")`. The runtime takes the next ID, stores a promise under it,
   and posts `ID␟reverse␟abc` through the transport. `␟` is U+001F, the ASCII unit separator: it
   can't appear in an identifier, and unlike NUL, it doesn't truncate a C string.
3. The engine delivers the string to the backend on the UI thread, and the backend calls
   `handleBridgeMessage`. [`BridgeProtocol.parse`](src/main/java/dev/ivchenko/lwjwae/bridge/BridgeProtocol.java) splits the text into at most three fields, so a
   payload can contain the separator. A message that doesn't parse is reported through
   [`ThrowableUtil`](src/main/java/dev/ivchenko/lwjwae/util/ThrowableUtil.java), not thrown: the caller is a native callback.
4. The handler runs on a virtual thread. A handler can block, on I/O or on the UI thread itself
   through `eval`, without freezing the window.
5. The result goes back with `eval(resolveScript(id, result))`. A handler that throws sends
   `rejectScript(id, message)` with the message of the root cause instead. Nothing is sent to a
   window that closed in the meantime.
6. The runtime settles the promise.

Every string that goes into a script passes through [`ScriptUtil.quote`](src/main/java/dev/ivchenko/lwjwae/util/ScriptUtil.java). A quote, a backslash, a
control character, or U+2028 in application data would otherwise change the script.

### From Java to the page: `emit`

`emit("tick", payload)` evaluates `emitScript`. The runtime calls every listener registered with
`window.lwjwae.on("tick", listener)` and dispatches a `CustomEvent` named `lwjwae:tick` with the
payload as `detail`. There's no answer.

### Typed calls

`bind(name, Class, handler)` and `emit(name, Object)` use the same protocol with the `typed` flag
set. A codec has two halves that must agree on a format:

- The Java half, [`BridgeCodec`](src/main/java/dev/ivchenko/lwjwae/bridge/codec/BridgeCodec.java):
  `encode(Object)` turns a value into text and `decode(String, Class)` turns text into the argument
  type.
- The page half, `pageScript()`: a JavaScript expression that evaluates to an object with
  `encode(value)` and `decode(text)`. The bridge runtime evaluates it once per document, before the
  scripts of the document. A JSON codec returns `JSON.stringify` and `JSON.parse` here; the core
  doesn't provide that text, every codec states its own.

A typed call goes: the page encodes the argument, Java decodes it into the argument type, encodes
the return value, and the page decodes it before the promise resolves. `emit` encodes on the Java
side and the page decodes before it calls the listeners. The format itself is the choice of the
codec: JSON is the obvious one, but anything that fits in a string works, for example MessagePack in
Base64, as long as the codec ships the matching page half.

The codec comes from `ApplicationParameters.codec()`, or from a codec module on the classpath
through `ServiceLoader`. Without one, the page has no encoder: an untyped call that passes an
object sends `String(payload)`, and `bind(name, Class, handler)` and `emit(name, Object)` fail at
the call site with `IllegalStateException`, not later on the page.

## Resources

`loadResource("app/index.html")` serves the files of the application from the classpath. WebKit
backends register a custom scheme and serve `app://local/app/index.html`; WebView2 has no custom
schemes, so the Windows backend intercepts `http://app.localhost/app/index.html` instead. Either
way, relative links, stylesheets, and `fetch` calls in the page resolve the way they resolve on a
web server. [`ResourceUtil.read`](src/main/java/dev/ivchenko/lwjwae/util/ResourceUtil.java) finds the bytes, and [`MimeTypeUtil.of`](src/main/java/dev/ivchenko/lwjwae/util/MimeTypeUtil.java) picks the media type from
the filename, because a wrong type on the main document makes the engine show markup as text.

When [`ApplicationParameters.devServerUrl()`](src/main/java/dev/ivchenko/lwjwae/ApplicationParameters.java) is set, `loadResource` opens that URL instead. Set it
through the builder, the `lwjwae.devServerUrl` system property, or the `LWJWAE_DEV_SERVER_URL`
environment variable, and a Vite or webpack development server with hot reload drives the window
while the Java side stays as it ships.

## Threading

Native UI toolkits are single-threaded. [`ui.UiDispatcher`](src/main/java/dev/ivchenko/lwjwae/ui/UiDispatcher.java) is the abstraction over the one thread
that a backend uses its toolkit from:

- `isDispatchThread()` tells whether the caller is already on it.
- `post(task)` hands work over without waiting.
- `call(action)` hands work over and waits for the result. Failures come back with their original
  type. A call made from the UI thread itself runs inline instead of deadlocking. A UI thread that
  doesn't answer within a minute is stuck, and the call fails with `IllegalStateException` instead
  of hanging.

[`ui.EventLoopDispatcher`](src/main/java/dev/ivchenko/lwjwae/ui/EventLoopDispatcher.java) is the variant for toolkits that accept any thread as long as it stays the
same one (GTK, Win32): it starts a daemon thread, initializes the toolkit on it, and runs the event
loop there for the rest of the process. A subclass supplies `initialize()`, `runEventLoop()`, and
`wakeUp()`, and calls `drainTasks()` from whatever the event loop invokes on wake-up. The macOS
backend can't use it, because Cocoa requires the main thread of the process, which the library
doesn't own.

## Native binding

Backends bind native libraries only through [`foreign.NativeLibraries`](src/main/java/dev/ivchenko/lwjwae/foreign/NativeLibraries.java):

- `load(sonames...)` opens the first library that the platform loader accepts.
- `downcall(library, symbol, descriptor)` binds a C function; `downcall(descriptor)` binds a
  function whose address comes at call time, which is how COM methods are called through vtables.
- `upcall(lookup, owner, method, type, descriptor)` exposes a static Java method as a C function
  pointer. The stub lives in the global arena, so one stub serves every window.

Every descriptor and every upcall target is recorded, and `downcalls()` and `upcalls()` return
them. A native image can only compile the stubs that it knew about at build time, so
[`NativeImageMetadataContractTest`](src/testFixtures/java/dev/ivchenko/lwjwae/testing/contract/NativeImageMetadataContractTest.java) compares those sets with the `reachability-metadata.json` that
ships in each backend JAR file. A stub that is bound but not registered fails the build here
instead of the binary of the user.

[`foreign.CallbackRegistry`](src/main/java/dev/ivchenko/lwjwae/foreign/CallbackRegistry.java) maps native `void*` user data to Java objects. C callbacks carry no
closure, only an opaque pointer, and object addresses aren't stable under a moving garbage
collector, so each object is registered under a generated ID and the ID travels as the pointer.

[`foreign.Layouts`](src/main/java/dev/ivchenko/lwjwae/foreign/Layouts.java) holds the C type layouts whose width doesn't depend on the data model. There is no
`C_LONG` on purpose: a C `long` is 64-bit on Unix LP64 and 32-bit on Windows LLP64, so each backend
declares the width that its own headers use.

## Writing a backend

A backend extends [`AbstractApplicationBackend`](src/main/java/dev/ivchenko/lwjwae/AbstractApplicationBackend.java), which owns the listener bookkeeping, the closed
signal, the blocking `run()` loop, and the whole bridge except its transport. The subclass
provides:

1. A [`UiDispatcher`](src/main/java/dev/ivchenko/lwjwae/ui/UiDispatcher.java), passed to the constructor.
2. The window methods of [`ApplicationBackend`](src/main/java/dev/ivchenko/lwjwae/ApplicationBackend.java): title, size, resizing, developer tools, navigation,
   `html`, `eval`, `show`, `close`. Each forwards to the dispatcher.
3. `injectOnDocumentStart(script)`, the engine facility that runs a script in every document before
   the scripts of the document.
4. `bridgeTransportScript()`, described earlier.
5. A call to `installBridge()` once the native view exists.
6. A call to `handleBridgeMessage(text)` from the callback that the engine delivers messages on, and
   calls to `emitLoad(event)` from the load callbacks.
7. A call to `markClosed()` when the native window is gone, which releases every thread blocked in
   `run()`.

Every native callback catches `Throwable` and passes it to [`ThrowableUtil.report`](src/main/java/dev/ivchenko/lwjwae/util/ThrowableUtil.java). Letting a Java
throwable unwind into C is undefined behavior.

## Test fixtures

`src/testFixtures` holds the tests that every backend module subclasses:

| Test                                                                                                                                 | Needs                      | Checks                                                                    |
|--------------------------------------------------------------------------------------------------------------------------------------|----------------------------|---------------------------------------------------------------------------|
| [`BackendSelectionContractTest`](src/testFixtures/java/dev/ivchenko/lwjwae/testing/contract/BackendSelectionContractTest.java)       | Nothing                    | The provider is registered and answers for its own platform only.         |
| [`NativeImageMetadataContractTest`](src/testFixtures/java/dev/ivchenko/lwjwae/testing/contract/NativeImageMetadataContractTest.java) | The native libraries       | The reachability metadata matches the bound stubs exactly.                |
| [`WindowContractTest`](src/testFixtures/java/dev/ivchenko/lwjwae/testing/contract/WindowContractTest.java)                           | A display                  | A window opens, renders a local HTTP page, and its properties round-trip. |
| [`BridgeContractTest`](src/testFixtures/java/dev/ivchenko/lwjwae/testing/contract/BridgeContractTest.java)                           | A display                  | Classpath resources, both directions of the bridge, typed calls, events.  |
| [`LifecycleContractTest`](src/testFixtures/java/dev/ivchenko/lwjwae/testing/contract/LifecycleContractTest.java)                     | A display                  | Failed loads, throwing scripts, closed windows, two windows at once.      |
| [`NetworkContractTest`](src/testFixtures/java/dev/ivchenko/lwjwae/testing/contract/NetworkContractTest.java)                         | A display and the internet | A real site renders. Never part of a default run.                         |
| [`BridgeCodecContractTest`](src/testFixtures/java/dev/ivchenko/lwjwae/testing/contract/BridgeCodecContractTest.java) | Nothing | A codec is discovered, round-trips records with nesting and escapes, rejects bad text, and ships a page half. |
| [`JsonBridgeCodecContractTest`](src/testFixtures/java/dev/ivchenko/lwjwae/testing/contract/JsonBridgeCodecContractTest.java) | Nothing | A JSON codec on top: canonical text, `null`, `JSON.stringify` and `JSON.parse` on the page. |

The display tests skip when no display is present, so a headless machine gets a passing build. With
`-Dlwjwae.requireDisplay=true`, a missing display fails the build instead, which is what CI runs
under Xvfb. `-Dlwjwae.screenshots=true` captures the screen while each window is open.

The bridge tests need a [`BridgeCodec`](src/main/java/dev/ivchenko/lwjwae/bridge/codec/BridgeCodec.java). A backend module registers [`PointCodec`](src/testFixtures/java/dev/ivchenko/lwjwae/testing/PointCodec.java) from the fixtures
in the `META-INF/services` of its test classpath, so the tests run without a JSON library.
