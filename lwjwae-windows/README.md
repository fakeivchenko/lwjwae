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
| [`WindowsTray`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsTray.java) | A tray icon in the notification area, through `Shell_NotifyIconW`. |
| [`WindowsNotifier`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsNotifier.java), [`WindowsNotification`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsNotification.java) | Notifications, as toasts. |
| [`binding.User32`](src/main/java/dev/ivchenko/lwjwae/windows/binding/User32.java)                                                                                                                                                                                   | The window class, the window, and the message loop.                |
| [`binding.Kernel32`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Kernel32.java)                                                                                                                                                                               | Module handles, thread IDs, `LoadLibraryExW`, `GetProcAddress`.    |
| [`binding.Ole32`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Ole32.java)                                                                                                                                                                                     | The COM apartment and task memory.                                 |
| [`binding.Advapi32`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Advapi32.java)                                                                                                                                                                               | The registry, to find the runtime.                                 |
| [`binding.Shlwapi`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Shlwapi.java)                                                                                                                                                                                 | An in-memory `IStream` for resource responses.                     |
| [`binding.Shell32`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Shell32.java) | `Shell_NotifyIconW`: adds, changes, and removes a tray icon. |
| [`binding.WinRt`](src/main/java/dev/ivchenko/lwjwae/windows/binding/WinRt.java), [`binding.Toasts`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Toasts.java) | `HSTRING`s and class activation from `combase.dll`, and the toast interfaces by vtable slot. |
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

## RPC

WebView2 reads a custom response to its end before the page sees any of it, and doesn't tell the
host when the page gives up on a request, so every call goes through web messages here,
`lwjwae.call` included. The protocol is the one of every engine's message channel, see
[`MessageRpcExchange`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/bridge/MessageRpcExchange.java);
the window only posts the answers:

1. `rpcTransportScript()` tells the bootstrap to send `lwjwae.call` as a message too and to take
   answers from the `message` and `sharedbufferreceived` events of `chrome.webview`.
2. [`WindowsMessageChannel`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsMessageChannel.java) posts a message with `PostWebMessageAsString`, on the UI thread.
3. It posts a part of 16 KiB or more as a shared buffer
   (`ICoreWebView2Environment12::CreateSharedBuffer`, `ICoreWebView2_17::PostSharedBufferToScript`),
   which reaches the page as an `ArrayBuffer` with no encoding; the page copies it and releases it.
   On a runtime older than 114, the part goes as Base64 in a text message instead.

## Window state

- **Minimize, maximize, restore.** `ShowWindow` with `SW_MINIMIZE`, `SW_MAXIMIZE`, and
  `SW_RESTORE`, read back with `IsIconic` and `IsZoomed`. `ShowWindow` would show a hidden window,
  which the other backends don't do, so a hidden window keeps the command until `show()`. A
  window that was maximized before it was minimized comes back maximized from `SW_RESTORE`, so
  `restore()` sends it a second time.
- **Full screen.** Windows has no such state: the window drops `WS_OVERLAPPEDWINDOW` and covers its
  monitor, and the style and the `WINDOWPLACEMENT` from before are kept to put back.
- **Limits.** The window procedure answers `WM_GETMINMAXINFO` with the limits turned into frame
  sizes by `AdjustWindowRectEx`, except in full screen. Setting a limit resizes the window to its
  own size, which runs it through the limits.
- **On top and focus.** `HWND_TOPMOST` and `WS_EX_TOPMOST`; `SetForegroundWindow` and
  `GetForegroundWindow`.
- **Without a title bar.** The window keeps `WS_OVERLAPPEDWINDOW`, so it snaps and animates as any
  other, and answers `WM_NCCALCSIZE` with the frame that `DefWindowProc` works out minus the part
  above the client area. The resize edges on the left, the right, and at the bottom stay outside
  the client area, where the web view doesn't reach; the top edge is a strip that the page lays over
  itself. A maximized window reaches past its monitor by the width of its frame, which the client
  area leaves out at the top too. The frame of `resizeClient` and of the limits has nothing above
  the client area then.
- **Buttons.** No `WS_MINIMIZEBOX` or `WS_MAXIMIZEBOX` for a window that may not have them, and
  `Close` grayed out in its system menu, which grays out the close button and takes away `Alt+F4`;
  `WM_CLOSE` is refused as well, since the taskbar sends it all the same.
- **Drags from the page.** `ReleaseCapture`, then `WM_NCLBUTTONDOWN` posted with `HTCAPTION` to move
  or `HTLEFT` and the like to resize: Windows runs the loop it runs for a press on the frame. Only
  while the primary button is down, by `GetAsyncKeyState`: after a quick click, the loop would wait
  for the next one.

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

## Tray

`Application.tray(TrayIcon)` creates a [`WindowsTray`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsTray.java):
an icon in the notification area, added with `Shell_NotifyIconW`. The shell reports clicks as a message
to a window, so each tray has a hidden window of its own on the UI thread. The window is a top-level
`WS_EX_TOOLWINDOW` window rather than a message-only one: only top-level windows receive the
`TaskbarCreated` broadcast that Explorer sends when it restarts, and the tray adds its icon again on it.
Without that, the icon would be gone for the rest of the process after an Explorer crash.

The PNG becomes an `HICON` through `CreateIconFromResourceEx`, which reads PNG data as it reads an icon
resource. A left click runs `onActivate`, or opens the menu when there is none; a right click, or the
context-menu key, opens the menu. The menu is built from the current entries on every click and shown
with `TrackPopupMenu` and `TPM_RETURNCMD`, so it answers with the entry picked and no menu handle
outlives the click. Before it opens, the tray window takes the foreground, and afterwards it posts
`WM_NULL`: the documented workaround without which the menu doesn't close on a click elsewhere. Entry
actions and `onActivate` run on a virtual thread, off the UI thread.

The tray closes with its application, on `quit()`, or earlier through `Tray.close()`. Until then it
keeps `Application.run()` going, so an application can live in the tray with no window open.

## Notifications

`Application.showNotification(Notification)` shows a toast, through a
[`WindowsNotifier`](src/main/java/dev/ivchenko/lwjwae/windows/WindowsNotifier.java) created on the
first notification. Toasts are a Windows Runtime API, and the Windows Runtime is COM: a runtime
object is called by vtable slot as WebView2 is, from [`binding.Toasts`](src/main/java/dev/ivchenko/lwjwae/windows/binding/Toasts.java),
and [`binding.WinRt`](src/main/java/dev/ivchenko/lwjwae/windows/binding/WinRt.java) adds the two
things COM lacks: `HSTRING`s and `RoGetActivationFactory`. The STA of the UI thread serves both.

Windows files toasts under an AppUserModelID. A plain executable has none, so the notifier registers
`lwjwae.<letters and digits of the name>.<hash of the name>` under `HKCU\Software\Classes\AppUserModelId`, with the name of the application from
`ApplicationParameters.name()`, or of the main class or the executable, as `DisplayName`. The hash
keeps apart names that differ only in characters that an ID can't hold, such as Cyrillic ones. The key stays after the
application exits: Notification Center labels the toasts that are still there with it. Without a
Start menu shortcut, Windows has no notification setting for the ID yet and `get_Setting` fails with
`ERROR_NOT_FOUND`; the notifier shows the toast anyway, and a toast that Windows refuses reports its
`Failed` event and closes.

The content is `ToastGeneric` XML: the title and the body as two text lines, the image as a `file:`
URI in the `appLogoOverride` placement, and each button as an action with the argument `action-N`.
A click on the toast itself has the argument `default`. `Activated`, `Dismissed`, and `Failed` fire
on a thread of the pool, not on the UI thread, so the handlers are agile COM objects: they answer to
`IAgileObject`, and their reference count is atomic. A toast that times out moves to Notification
Center, where it can still be clicked, so its handle stays open until a click, a dismissal, `close()`,
or `quit()`. Under Do Not Disturb, every toast goes there at once and reports a timeout; taking those
back would hide every notification from a user who only asked for quiet.

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
