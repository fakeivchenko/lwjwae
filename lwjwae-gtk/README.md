# lwjwae-gtk

The Linux backend: a GTK 3 window with a WebKitGTK 4.1 view.

## Requirements

- Linux or a BSD with a desktop.
- `libgtk-3.so.0` and `libwebkit2gtk-4.1.so.0` on the loader path, that is, GTK 3 and WebKitGTK
  2.40 or newer with the 4.1 API (libsoup 3).
- An X11 or Wayland display.

| Distribution                       | Packages                                                        |
|------------------------------------|-----------------------------------------------------------------|
| Debian 12+                         | `libgtk-3-0 libwebkit2gtk-4.1-0`                                |
| Ubuntu 22.04+                      | `libgtk-3-0t64 libwebkit2gtk-4.1-0` (`libgtk-3-0` before 24.04) |
| Fedora 38+, RHEL 9 and derivatives | `gtk3 webkit2gtk4.1`                                            |
| Arch                               | `gtk3 webkit2gtk-4.1`                                           |
| openSUSE                           | `libgtk-3-0 libwebkit2gtk-4_1-0`                                |
| Alpine                             | `gtk+3.0 webkit2gtk-4.1`                                        |

When a library is missing, the message of `BackendNotAvailableException` names it and, on the
distributions above and their derivatives (found through `ID_LIKE` in `/etc/os-release`), the
command that installs it. Distributions that ship only WebKitGTK 4.0 (Debian 11, Ubuntu 20.04,
RHEL 8) aren't supported.

The provider [`GtkBackendProvider`](src/main/java/dev/ivchenko/lwjwae/gtk/GtkBackendProvider.java) registers under the name `gtk3-webkit2gtk-4.1`. It
checks the operating system first and the libraries second, because probing a library means
`dlopen`, and WebKitGTK is about 90 MB.

## Layout

| Class                                                                                                                                                    | Role                                                                                        |
|----------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| [`GtkApplication`](src/main/java/dev/ivchenko/lwjwae/gtk/GtkApplication.java)                                                                            | The application. Prepares the web context and serves `app://`; opens `GtkWindow`s.          |
| [`GtkWindow`](src/main/java/dev/ivchenko/lwjwae/gtk/GtkWindow.java)                                                                                      | The window. Forwards every call to the GTK thread.                                          |
| [`GtkDispatcher`](src/main/java/dev/ivchenko/lwjwae/gtk/GtkDispatcher.java)                                                                              | The one GTK thread of the process.                                                          |
| [`GtkTray`](src/main/java/dev/ivchenko/lwjwae/gtk/GtkTray.java)                                                                                          | A tray icon, through libappindicator or `GtkStatusIcon`.                                    |
| [`binding.Gtk`](src/main/java/dev/ivchenko/lwjwae/gtk/binding/Gtk.java)                                                                                  | `gtk_*` functions: the window and the main loop.                                            |
| [`binding.WebKit`](src/main/java/dev/ivchenko/lwjwae/gtk/binding/WebKit.java)                                                                            | `webkit_*` functions: the view, user scripts, message handlers, the URI scheme, evaluation. |
| [`binding.AppIndicator`](src/main/java/dev/ivchenko/lwjwae/gtk/binding/AppIndicator.java)                                                                | `app_indicator_*` functions, from an optional library.                                      |
| [`binding.Signatures`](src/main/java/dev/ivchenko/lwjwae/gtk/binding/Signatures.java)                                                                    | Every `FunctionDescriptor` the module binds, named by shape.                                |

The binding classes are Lombok `@UtilityClass`es with `static final` method handles. `invokeExact`
compiles to a direct call only when the JIT compiler sees the handle as a constant.

## The GTK thread

GTK can only be used from the thread that ran `gtk_init`. [`GtkDispatcher`](src/main/java/dev/ivchenko/lwjwae/gtk/GtkDispatcher.java) extends
[`EventLoopDispatcher`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/ui/EventLoopDispatcher.java) and owns that thread for the process:

1. [`GtkDispatcher.instance()`](src/main/java/dev/ivchenko/lwjwae/gtk/GtkDispatcher.java) starts a daemon thread named `lwjwae-gtk` on first use and blocks
   until initialization finished.
2. On that thread, `initialize()` calls `gtk_init_check(NULL, NULL)`. A `false` return means no
   display, and the failure surfaces as `IllegalStateException` in the caller instead of a hang.
3. `runEventLoop()` calls `gtk_main()`, which never returns.
4. Work posted from another thread goes into a queue, and `wakeUp()` calls `g_idle_add` with a
   shared `GSourceFunc` stub. `g_idle_add` is the one GLib entry point that's safe from any thread.
   The stub drains the queue and returns `G_SOURCE_REMOVE`.

## Creating the application

`new GtkApplication(parameters)` starts the GTK thread if it isn't running and does two things on
it, once per process, because neither can be undone:

1. Takes one permanent reference to the default `WebKitWebContext`. WebKit installs an `atexit`
   handler that decrements the reference count of the context. If the last view is already gone by
   then, the count drops to zero and WebKit aborts while it disposes the website data manager. The
   extra reference keeps the process-wide singleton alive.
2. Registers the `app` URI scheme on the default context, with a static callback. Every view of
   every application shares it.

## Creating a window

`application.open(parameters)` runs the following on the GTK thread and returns when it's done:

1. Creates a `GtkWindow` (`GTK_WINDOW_TOPLEVEL`) with the title and the default size from the
   parameters. A requested position goes to `gtk_window_move` before the window is mapped; a
   centered window gets `GTK_WIN_POS_CENTER`. Later, `center()` reads the work area of the monitor
   from GDK and moves the frame itself, except on Wayland, where a client can't move its window and
   the backend falls back to `GTK_WIN_POS_CENTER` and hopes.
2. Creates a `WebKitUserContentManager`, connects `script-message-received::__lwjwaeBridge` to the
   bridge callback, and then registers the `__lwjwaeBridge` message handler. The order matters:
   an early message would otherwise race the signal connection.
3. Creates the `WebKitWebView` with that content manager and adds it to the window.
4. Connects `destroy` on the window and `load-changed`, `load-failed`, and `context-menu` on the
   view. Every handler is a static upcall stub; the user data is the ID of the window in a
   [`CallbackRegistry`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/foreign/CallbackRegistry.java).
5. Calls `installBridge()`, which adds the bridge runtime as a user script at document start.

The window stays hidden until `show()`, which calls `gtk_widget_show_all`.

## Callbacks

Every signal handler follows the same shape: look up the window by user data, do the work, catch
`Throwable` and report it. Nothing unwinds into GTK.

| Signal                    | Handler           | What it does                                                                                                                                                                                                                          |
|---------------------------|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `load-changed`            | `onLoadChanged`   | Maps `WebKitLoadEvent` to [`LoadState`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/event/LoadState.java) and emits a [`LoadEvent`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/event/LoadEvent.java) with the current URI. |
| `load-failed`             | `onLoadFailed`    | Emits `LoadEvent.failed` with the failing URI and the `GError` message. Returns `FALSE`, so WebKit renders its own error page.                                                                                                        |
| `context-menu`            | `onContextMenu`   | Returns `TRUE` to suppress the engine menu, unless the developer tools are on: "Inspect Element" lives in that menu.                                                                                                                  |
| `script-message-received` | `onBridgeMessage` | Reads the string of the `WebKitJavascriptResult` and calls `handleBridgeMessage`.                                                                                                                                                     |
| `destroy`                 | `onDestroy`       | Unregisters the window, clears the handles, and calls `markClosed()`.                                                                                                                                                                 |

## Serving resources

The `app://local/PATH` scheme is answered by `onResourceRequest` in `GtkApplication`, which isn't tied to a window:

1. Reads the path of the `WebKitURISchemeRequest`.
2. Reads the bytes from the classpath with [`ResourceUtil.read`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/util/ResourceUtil.java).
3. Copies them into a buffer that GLib owns (`g_malloc`), because the stream outlives the call and
   a moving garbage collector could relocate a Java array.
4. Wraps the buffer in a `GMemoryInputStream` that frees it on disposal, and finishes the request
   with the stream, the length, and the media type from [`MimeTypeUtil`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/util/MimeTypeUtil.java).
5. On [`ResourceNotFoundException`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/exception/ResourceNotFoundException.java), finishes the request with a `GError` of code 404 whose message
   names the missing path. WebKit reports that to `load-failed`, so the application sees a
   [`LoadEvent.failed`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/event/LoadEvent.java) like for any other URL.

## Evaluating scripts

`eval(script)` doesn't block:

1. Registers the future under an ID in a [`CallbackRegistry`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/foreign/CallbackRegistry.java).
2. Posts `webkit_web_view_evaluate_javascript` to the GTK thread with a shared
   `GAsyncReadyCallback` stub and the ID as user data.
3. When the callback fires, `webkit_web_view_evaluate_javascript_finish` returns a `JSCValue` or
   fills a `GError`. The value is rendered with `jsc_value_to_string` and completes the future; the
   error becomes [`ScriptEvaluationFailedException`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/exception/ScriptEvaluationFailedException.java).

WebKitGTK reports the message of a thrown error itself, so this backend doesn't need the tagged
wrapper that the Windows and macOS backends use.

## Developer tools

`devToolsEnabled(true)` sets `enable-developer-extras` on the `WebKitSettings` of the view. That
enables the Web Inspector and adds "Inspect Element" to the context menu, which the backend then
stops suppressing. `isDevToolsEnabled()` reads the same setting back.

## Tray

`Application.tray(TrayIcon)` takes the first of three ways that works:

1. **StatusNotifierItem** over D-Bus, through libappindicator (`libayatana-appindicator3.so.1` or
   `libappindicator3.so.1`), when the library loads, in a
   [`GtkTray`](src/main/java/dev/ivchenko/lwjwae/gtk/GtkTray.java). This is what KDE, GNOME with
   its AppIndicator extension, and most other panels take, and the only tray that works on Wayland.
   The indicator shows the menu on any click; there is no primary-click event, so `onActivate`
   never runs on this path.
2. **StatusNotifierItem** served by the backend itself, when libappindicator is missing and the
   session has a StatusNotifier host: the
   [`StatusNotifierTray`](../lwjwae-glib/src/main/java/dev/ivchenko/lwjwae/glib/StatusNotifierTray.java)
   of [`lwjwae-glib`](../lwjwae-glib#tray), which the GTK 4 backend uses too. It reaches the same
   panels, and `Activate` runs `onActivate`.
3. **XEmbed**, through `GtkStatusIcon`, in a `GtkTray`, otherwise. Deprecated in GTK 3.14 but still
   in the library; needs X11 and a panel with a legacy tray. The `activate` signal runs
   `onActivate`, and `popup-menu` opens the menu at the pointer.

With `GtkTray`, both hosts read the image from a file, and a StatusNotifier host caches it by name, so every image
becomes a fresh PNG file under a name that no previous image had, in a temporary directory that the
tray deletes when it closes. The menu is a `GtkMenu` with one `activate` handler per labeled entry;
each entry has its own ID in a `CallbackRegistry`, so one upcall stub serves every entry of every
tray. Entry actions and `onActivate` run on a virtual thread, off the GTK thread.

The library is optional, so [`binding.AppIndicator`](src/main/java/dev/ivchenko/lwjwae/gtk/binding/AppIndicator.java)
binds through `NativeLibraries.downcallIfPresent`, which records the descriptor whether the library
loaded or not: the reachability metadata of the module stays the same on a machine without it.

The tray closes with its application, on `quit()`, or earlier through `Tray.close()`. Until then it
keeps `Application.run()` going, so an application can live in the tray with no window open.

## Notifications

`Application.showNotification(Notification)` goes through the
[`FreedesktopNotifier`](../lwjwae-glib/src/main/java/dev/ivchenko/lwjwae/glib/FreedesktopNotifier.java)
of [`lwjwae-glib`](../lwjwae-glib#notifications): `org.freedesktop.Notifications` over GDBus, the
same under GTK 3 and GTK 4.

## Closing

`close()` calls `gtk_widget_destroy` on the window, on the GTK thread. GTK emits `destroy`
synchronously, so `onDestroy` runs before `close()` returns, the window leaves the list of the
application, and if it was the last one, every thread blocked in `Application.run()` is released.
Closing the window with the title bar button takes the same path. `close()` is idempotent.
`Application.quit()` closes every window this way; GTK itself keeps running, because `gtk_init`
can't be called twice, and another application can be created on the same thread.

## Tests

```bash
./gradlew :lwjwae-gtk:test          # Headless: provider registration, reachability metadata
./gradlew :lwjwae-gtk:displayTest   # Opens real windows; skips without a display
./gradlew :lwjwae-gtk:networkTest   # Loads google.com; never part of a default run
```

The metadata test initializes the binding classes, which loads GTK and WebKitGTK but opens no
window, so it runs on a headless Linux machine. On CI, run the display tests under Xvfb with
`-Dlwjwae.requireDisplay=true`, and keep `GDK_BACKEND=x11`,
`WEBKIT_DISABLE_DMABUF_RENDERER=1`, and `WEBKIT_DISABLE_COMPOSITING_MODE=1` in the environment,
because a runner has no GPU.
