# lwjwae-windows

The Windows backend: a Win32 window with a WebView2 view, no `WebView2Loader.dll`.

## Requirements

- Windows, x64.
- The WebView2 Evergreen runtime. Windows 11 ships it; on Windows 10, Edge installs it.

The provider [`WindowsBackendProvider`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsBackendProvider.java) registers under the name `win32-webview2`. It
checks the operating system first, so the JAR file is harmless on a Linux or macOS classpath, and
then looks for the runtime in the registry without loading it.

## Layout

| Class                                                                                                                                                                                                                                                               | Role                                                               |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| [`WindowsApplication`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsApplication.java)                                                                                                                                                                           | The application. Owns the WebView2 environment; opens windows.     |
| [`WindowsWindow`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsWindow.java)                                                                                                                                                                                     | The window. Forwards every call to the UI thread.                  |
| [`WindowsDispatcher`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsDispatcher.java)                                                                                                                                                                             | The one UI thread of the process, which is also the COM apartment. |
| [`binding.User32`](src/main/java/dev/ivchenko/lwjwae/windows/binding/User32.java)                                                                                                                                                                                   | The window class, the window, and the message loop.                |
| [`binding.Kernel32`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Kernel32.java)                                                                                                                                                                               | Module handles, thread IDs, `LoadLibraryExW`, `GetProcAddress`.    |
| [`binding.Ole32`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Ole32.java)                                                                                                                                                                                     | The COM apartment and task memory.                                 |
| [`binding.Advapi32`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Advapi32.java)                                                                                                                                                                               | The registry, to find the runtime.                                 |
| [`binding.Shlwapi`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Shlwapi.java)                                                                                                                                                                                 | An in-memory `IStream` for resource responses.                     |
| [`binding.WebView2Runtime`](src/main/java/dev/ivchenko/lwjwae/windows/binding/WebView2Runtime.java)                                                                                                                                                                 | Where `EmbeddedBrowserWebView.dll` is.                             |
| [`binding.WebView2`](src/main/java/dev/ivchenko/lwjwae/windows/binding/WebView2.java)                                                                                                                                                                               | The `ICoreWebView2*` methods the backend uses, by vtable slot.     |
| [`binding.Com`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Com.java)                                                                                                                                                                                         | Calling a COM method through its vtable.                           |
| [`binding.ComCallback`](src/main/java/dev/ivchenko/lwjwae/windows/binding/ComCallback.java), [`ComCompletion`](src/main/java/dev/ivchenko/lwjwae/windows/binding/ComCompletion.java), [`ComEvent`](src/main/java/dev/ivchenko/lwjwae/windows/binding/ComEvent.java) | COM objects implemented in Java.                                   |
| [`binding.Wide`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Wide.java)                                                                                                                                                                                       | UTF-16 strings.                                                    |
| [`binding.Signatures`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Signatures.java)                                                                                                                                                                           | Every `FunctionDescriptor` the module binds, named by shape.       |
| [`exception.ComCallFailedException`](src/main/java/dev/ivchenko/lwjwae/windows/exception/ComCallFailedException.java)                                                                                                                                               | A failure `HRESULT`.                                               |
| [`util.JsonStringUtil`](src/main/java/dev/ivchenko/lwjwae/windows/util/JsonStringUtil.java)                                                                                                                                                                         | Decodes the JSON string that `ExecuteScript` returns.              |

## Finding the runtime without the loader

The official entry point is `WebView2Loader.dll` from the SDK, a native artifact. The loader is
thin: it reads the install location of the runtime from the registry and loads
`EmbeddedBrowserWebView.dll` from there. [`WebView2Runtime.library()`](src/main/java/dev/ivchenko/lwjwae/windows/binding/WebView2Runtime.java) does the same:

1. Reads the `EBWebView` value under the Edge Update client state key of the runtime,
   `{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}`, first under `HKLM\SOFTWARE\WOW6432Node`, then under
   `HKCU\SOFTWARE`.
2. Appends `EBWebView\x64\EmbeddedBrowserWebView.dll` and checks that the file exists.

`WebView2.CREATE_ENVIRONMENT_INTERNAL` then loads that DLL with `LoadLibraryExW` and the
`LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR` flag, so its private dependencies resolve, and binds its one
export, `CreateWebViewEnvironmentWithOptionsInternal`. That's what the public
`CreateCoreWebView2EnvironmentWithOptions` of the loader calls after it located the runtime.

## COM without a compiler

A COM object is a pointer to a struct whose first word points to a table of function pointers, and
a method call is a C call through slot *n* with the object as the first argument. [`Com`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Com.java) keeps one
`MethodHandle` per method *shape* and reads the slot at call time. The slot numbers come from
[`WebView2.h`](src/main/java/dev/ivchenko/lwjwae/windows/binding/WebView2.java) in SDK 1.0.4191.47. Only the original, unversioned interfaces are used, which every
runtime implements.

The callbacks that WebView2 makes into Java are COM objects built in Java. Every handler has the
same shape, `IUnknown` plus one `Invoke`, and `Invoke` has two signatures: `(HRESULT, T*)` for a
completion and `(sender, args)` for an event. So [`ComCallback`](src/main/java/dev/ivchenko/lwjwae/windows/binding/ComCallback.java) has exactly two vtables, shared by
every instance, and an instance is a 40-byte struct: the vtable pointer, an ID into a
[`CallbackRegistry`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/foreign/CallbackRegistry.java), a reference count, and the IID that the object answers to. `QueryInterface`
answers `IUnknown` and that IID. Reference counting is real: WebView2 holds a completion handler
until it fires and an event handler until the view closes, and when the count reaches zero, the
entry is dropped.

The ownership rule: [`ComCallback.completion`](src/main/java/dev/ivchenko/lwjwae/windows/binding/ComCallback.java) and `ComCallback.event` return an object with one
reference. Pass it to WebView2, which takes its own, and then call [`Com.release`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Com.java).

## The UI thread

WebView2 lives in a single-threaded apartment. The thread that creates a controller owns it,
receives its callbacks, and must pump messages for them to arrive. [`WindowsDispatcher`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsDispatcher.java) extends
[`EventLoopDispatcher`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/ui/EventLoopDispatcher.java):

1. [`WindowsDispatcher.instance()`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsDispatcher.java) starts a daemon thread named `lwjwae-win32` on first use.
2. On that thread, `initialize()` calls `CoInitializeEx(NULL, COINIT_APARTMENTTHREADED)`, records
   the thread ID, and calls `PeekMessageW` once. Until a thread has called a message function, it
   has no message queue, and `PostThreadMessage` to it fails, so the first wake-up would be lost.
3. `runEventLoop()` runs `GetMessageW`, and for every message either drains the task queue, when
   the message is `WM_APP`, or calls `TranslateMessage` and `DispatchMessageW`.
4. `wakeUp()` posts `WM_APP` to the thread with `PostThreadMessageW`, which is safe from any thread.

## Creating the application

`new WindowsApplication(parameters)` starts the UI thread if it isn't running, then, on it, calls
`CreateWebViewEnvironmentWithOptionsInternal` with a user data folder under
`%LOCALAPPDATA%\lwjwae\WebView2` and a completion handler, and waits up to 60 seconds for the
`ICoreWebView2Environment` (see [Waiting on the UI thread](#waiting-on-the-ui-thread)). One environment means one browser process and
one profile for every window of the application. `engine()` reads its browser version. The
environment is released on the UI thread once `quit()` closed the last window.

## Waiting on the UI thread

WebView2 answers `CreateWebViewEnvironmentWithOptionsInternal` and `CreateCoreWebView2Controller`
through the message queue of the UI thread. Any other thread just blocks on the future.
The UI thread can't: blocking it would starve the very completion it waits for. So
`WindowsDispatcher.await` runs a nested `GetMessageW` loop there until the future settles or the
deadline passes, the way a modal dialog does. This is what lets an `onLoad` listener, which runs on
the UI thread, open a window. Everything the outer loop would have run in the meantime, queued
tasks and callbacks of other windows included, runs inside that wait.

## Creating a window

`application.open(parameters)` does the following:

1. Registers the window in `WINDOWS`, a [`CallbackRegistry`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/foreign/CallbackRegistry.java), and gets an ID.
2. On the UI thread, registers the window class `lwjwae` once per process, with the static
   `windowProc` stub. The class loads icon resource 1 of the running module, so a native image
   built with an icon shows it in the title bar and the taskbar, and `java.exe` shows the stock
   icon.
3. Creates the window with `CreateWindowExW` and `WS_OVERLAPPEDWINDOW`, hidden, at the requested
   position or at `CW_USEDEFAULT`. Stores the registry ID in the `GWLP_USERDATA` slot of the
   window, which is how `windowProc` finds the window. Resizes the window so that the client area,
   not the outer frame, has the requested size, and centers it in the work area of its monitor
   (`MonitorFromWindow`, `GetMonitorInfoW`) when asked. `position()` reads `GetWindowRect`;
   `position(x, y)` is `SetWindowPos` with `SWP_NOSIZE`.
4. Calls `CreateCoreWebView2Controller` on the environment of the application with the window
   handle and a completion handler.
5. Back on the calling thread, waits up to 60 seconds on a future that the completion settles
   (see [Waiting on the UI thread](#waiting-on-the-ui-thread)).
6. In `onControllerCreated`, keeps a reference to the controller, gets the `ICoreWebView2`, sizes
   the view to the client area, switches the developer tools and the default context menu off,
   subscribes to `NavigationStarting`, `ContentLoading`, `NavigationCompleted`,
   `WebMessageReceived`, and `WebResourceRequested`, adds the `http://app.localhost/*` filter,
   calls `installBridge()`, and completes the future.

A failure at any step, including the timeout, unregisters the window, destroys it, and rethrows.

## The window procedure

`windowProc` is one static stub for every window. It looks the window up through
`GWLP_USERDATA`, handles two messages, and hands everything to `DefWindowProcW`:

- `WM_SIZE`: resizes the WebView2 controller to the new client area.
- `WM_DESTROY`: unregisters the window, closes the controller, releases the COM references, and
  calls `markClosed()` last, so that the application releases the environment only after the
  controller is gone.

## Events

| Event                  | Handler                                                                                                                                  | What it does |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------------|--------------|
| `NavigationStarting`   | Emits `LoadEvent.of(STARTED, uri)` with the URI of the event.                                                                            |
| `ContentLoading`       | Emits `LoadEvent.of(COMMITTED, source)`.                                                                                                 |
| `NavigationCompleted`  | Emits `FINISHED`, or `LoadEvent.failed` with the name of the `COREWEBVIEW2_WEB_ERROR_STATUS`.                                            |
| `WebMessageReceived`   | Reads the message as a string with `TryGetWebMessageAsString` and calls `handleBridgeMessage`. A message that isn't a string is ignored. |
| `WebResourceRequested` | Serves the resource, described next.                                                                                                     |

## Serving resources

WebView2 has no custom URI schemes, so classpath resources live under `http://app.localhost/`.
Requests to that origin are intercepted before they reach the network, and `.localhost` is a
secure context in Chromium. For every intercepted request:

1. Reads the URI, strips the origin and the query string, and reads the bytes from the classpath.
2. Copies the bytes into an `IStream` with `SHCreateMemStream`, and builds a response with
   `ICoreWebView2Environment::CreateWebResourceResponse`: status 200, a `Content-Type` header from
   [`MimeTypeUtil`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/util/MimeTypeUtil.java).
3. On [`ResourceNotFoundException`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/exception/ResourceNotFoundException.java), builds a 404 response with an empty body. When the request is
   for the main document, also emits [`LoadEvent.failed`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/event/LoadEvent.java) with the message, because Chromium renders
   its own error page for a 404 and reports the navigation as successful.
4. Puts the response on the event arguments and releases it.

`resourceUrl(path)` returns the `http://app.localhost/PATH` form, so `loadResource` navigates to
the right place.

## Evaluating scripts

`ICoreWebView2::ExecuteScript` reports a thrown exception as a `null` result instead of a failure,
and serializes every result as JSON. So `eval(script)` doesn't send the script as is:

1. Wraps it with [`ScriptUtil.taggedEvaluation`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/util/ScriptUtil.java): the text of the caller runs through `(0, eval)` in
   the global scope inside a `try`, and the function returns `"S"` plus the `String()` form of the
   value, or `"E"` plus the message of the error.
2. Posts `ExecuteScript` with a completion handler.
3. In the completion, decodes the JSON string with [`JsonStringUtil.decode`](src/main/java/dev/ivchenko/lwjwae/windows/util/JsonStringUtil.java), and hands the tagged
   text to `ScriptUtil.completeTagged`, which completes the future with the value or a
   [`ScriptEvaluationFailedException`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/exception/ScriptEvaluationFailedException.java) that carries the original message.

## Developer tools

`devToolsEnabled(true)` sets `AreDevToolsEnabled` and `AreDefaultContextMenusEnabled` on the
`ICoreWebView2Settings`. A shipped application wants neither: the menu offers "Reload", "View
source", and "Inspect". With the tools on, the menu stays, because "Inspect" lives there.
`isDevToolsEnabled()` reads `AreDevToolsEnabled` back.

## Closing

`close()` calls `DestroyWindow` on the UI thread. Windows sends `WM_DESTROY` synchronously, so the
window procedure completes the close before `close()` returns, and the last window to close
releases every thread blocked in `Application.run()`. Closing the window with the title bar button
takes the same path. `close()` is idempotent. `Application.quit()` closes every window this way and
then releases the environment; the UI thread and the window class stay for the process.

## Tests

The module compiles and runs its headless tests on any platform; the metadata and display tests
need Windows.

```bash
./gradlew :lwjwae-windows:test          # Headless: provider registration, JSON decoding, metadata on Windows
./gradlew :lwjwae-windows:displayTest   # Opens real windows; Windows only
```

To develop from Linux, drive a Windows VM over SSH. `WIN_HOST` names the SSH host (default
`win`), `WIN_SRC_DIR` the parent directory on the VM (default `C:\src`):

```bash
scripts/windows/sync-to-vm.sh :lwjwae-windows:test
```

```bash
scripts/windows/run-in-session.sh :lwjwae-windows:displayTest -Dlwjwae.requireDisplay=true
```

The second script exists because a command that runs over SSH lands in session 0. In that
session, windows have no desktop and WebView2 refuses to attach with
`ERROR_INVALID_WINDOW_HANDLE`. The script writes the command to a batch file, runs it through a
scheduled task in the session of the logged-on user, and streams the log back.
