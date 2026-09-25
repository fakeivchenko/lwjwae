<h1 align="center">lwjwae</h1>

<p align="center">
  <b>Lightweight Java web application engine</b><br/>
  <i>No JNI. No native code. No native artifacts.</i>
</p>

<p align="center">
  <a href="https://github.com/fakeivchenko/lwjwae/actions/workflows/tests.yml"><img alt="Tests" src="https://github.com/fakeivchenko/lwjwae/actions/workflows/tests.yml/badge.svg?branch=dev"></a>
  <a href="https://github.com/fakeivchenko/lwjwae/actions/workflows/release.yml"><img alt="Release" src="https://github.com/fakeivchenko/lwjwae/actions/workflows/release.yml/badge.svg?branch=release"></a>
  <a href="https://repo.ivchenko.dev/#/releases/dev/ivchenko/lwjwae/lwjwae-core"><img alt="Latest release" src="https://repo.ivchenko.dev/api/badge/latest/releases/dev/ivchenko/lwjwae/lwjwae-core?color=40c14a&name=release"></a>
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-blue">
  <img alt="GraalVM native-image ready" src="https://img.shields.io/badge/GraalVM-native--image%20ready-2f6fd6">
  <a href="LICENSE"><img alt="Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-green"></a>
</p>

Native desktop applications with a web view inside, from Java, through the Foreign Function and Memory
API. No JNI, no native artifacts of the library's own: on each platform, the window and the engine
are the ones that the operating system already has.

| Platform | Window | Engine        | Module                             | JAR                 | Native Image        |
|----------|--------|---------------|------------------------------------|---------------------|---------------------|
| Linux    | GTK 3  | WebKitGTK 4.1 | [`lwjwae-gtk`](lwjwae-gtk)         | <center>✅</center> | <center>✅</center> |
| Linux    | GTK 4  | WebKitGTK 6.0 | [`lwjwae-gtk4`](lwjwae-gtk4)       | <center>✅</center> | <center>✅</center> |
| Windows  | Win32  | WebView2      | [`lwjwae-windows`](lwjwae-windows) | <center>✅</center> | <center>✅</center> |
| macOS    | Cocoa  | WKWebView     | [`lwjwae-macos`](lwjwae-macos)     | <center>✅</center> | <center>✅</center> |

You write the interface in HTML, CSS, and JavaScript, ship it inside the native executable or JAR-package,
and call Java from the page and the page from Java through one bridge that works the same on every engine.

![The showcase example as a native executable on Windows 11: a Win32 window with a WebView2 page inside](docs/showcase-windows.png)

The window above is native compiled application created using lwjwae, compiled with
GraalVM to one executable and captured on Windows 11.

## A first window

```java
try (Application application = Application.create()) {
  Window window = application.open(WindowParameters.builder()
      .title("Docs")
      .width(1280)
      .height(800)
      .build());
  window.bind("reverse", text -> new StringBuilder(text).reverse().toString());
  window.loadResource("app/index.html");
  window.show();
  application.run();
}
```

On the page:

```js
const reversed = await window.reverse("lwjwae");
await window.lwjwae.listen("tick", (event) => console.log(event.payload));
```

`Application.create` picks the backend for the machine it runs on. Put
[`lwjwae-core`](lwjwae-core) and the backend module of your platform on the classpath, or all
three, and the same JAR file runs everywhere. [`lwjwae-examples`](https://github.com/fakeivchenko/lwjwae-examples) has a demo
that shows every feature on one page.

## Requirements

- **Java 25** or newer. The library binds native code through the FFM API and needs
  `--enable-native-access=ALL-UNNAMED` on the command line of the JVM, or the equivalent
  `Enable-Native-Access` manifest attribute in an executable JAR file.
- **A display**: X11 or Wayland on Linux, a desktop session on Windows and macOS. A service session
  without one, such as session 0 on Windows, can't open a window.
- The window and the engine of the platform:

| Platform | Needs                                                                                                                                                            |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Linux    | GTK 3 and WebKitGTK 2.40 or newer with the 4.1 API (`libwebkit2gtk-4.1.so.0`). See [`lwjwae-gtk`](lwjwae-gtk#requirements) for the package of each distribution. Or GTK 4 and the 6.0 API (`libwebkitgtk-6.0.so.4`) with [`lwjwae-gtk4`](lwjwae-gtk4#requirements), which can't place windows. |
| Windows  | Windows 10 or 11, x64, with the WebView2 Evergreen runtime. Windows 11 ships it; Edge installs it on Windows 10.                                                 |
| macOS    | macOS on arm64 or x86_64. AppKit and WebKit are part of the system.                                                                                              |

When a backend can't run, `Application.create` throws `BackendNotAvailableException` with the
reason of every backend it found and, on Linux, the command that installs the missing packages.

## Installation

The modules are published to `https://repo.ivchenko.dev/releases` under the group
`dev.ivchenko.lwjwae`:

```kotlin
repositories {
    maven("https://repo.ivchenko.dev/releases")
}

dependencies {
    implementation("dev.ivchenko.lwjwae:lwjwae-core:VERSION")
    runtimeOnly("dev.ivchenko.lwjwae:lwjwae-gtk:VERSION")
    runtimeOnly("dev.ivchenko.lwjwae:lwjwae-gtk4:VERSION") // optional: Linux without WebKitGTK 4.1
    runtimeOnly("dev.ivchenko.lwjwae:lwjwae-windows:VERSION")
    runtimeOnly("dev.ivchenko.lwjwae:lwjwae-macos:VERSION")
}
```

`lwjwae-core` is the API; a backend module is needed at runtime only. Add the one of your platform,
or all three, and the same JAR file runs everywhere: a backend checks the operating system before
it loads anything, so the other two step aside.

The typed bridge methods, `bind(name, Class, handler)` and `emit(name, Object)`, need a
`BridgeCodec`. The codecs live in [lwjwae-codecs](https://github.com/fakeivchenko/lwjwae-codecs): Jackson 3, Gson,
and Jakarta JSON Binding, each one a module that registers itself. Add one to the runtime classpath, or pass a
configured instance through `ApplicationParameters.codec()`:

```kotlin
dependencies {
    runtimeOnly("dev.ivchenko.lwjwae.codec:lwjwae-codec-jackson:VERSION")
}
```

## What you get

- **One API for three engines.** Title, size, resizing, navigation, inline HTML, script evaluation,
  load events, the developer tools. Every method works from any thread; the backend forwards it to
  the UI thread of the toolkit.
- **As many windows as you need.** An `Application` owns the UI thread, the codec, and the list of
  windows; `open` adds a `Window`, `run` blocks while any is open, `quit` closes them all. A page
  opens and closes windows too, through `window.lwjwae.open(options)` and `window.lwjwae.close()`.
- **A bridge in both directions, at two levels.** `bind` exposes a Java function to the page as
  `window.NAME(payload)`, which returns a promise. `emit` delivers an event to the page. On a window,
  they belong to that window; on the application, a binding reaches every window and learns which
  one called, a listener hears every window, and `emit` reaches every page. Handlers run on virtual
  threads, so a slow one doesn't freeze the window. The core has no serialization dependency; a
  codec adds typed calls with records and objects. Pages of other origins can't call Java.
- **Calls with bytes and streams.** `handle` answers `lwjwae.call(name, body)` on the page with a
  `Response`: text, bytes, a value through the codec, or a stream that the page reads while Java
  writes it, several hundred MB/s. An `AbortSignal` reaches the handler. Only the pages of the
  application, and the development server, may call.
- **Windows under control.** Minimize, maximize, full screen, on top, focus, size limits, and events
  for every change of a window in Java and on the page. With a state key, a window opens the way it
  last closed.
- **Native dialogs.** `showOpenDialog`, `showSaveDialog`, and `showMessageDialog` on a window, and
  `lwjwae.dialog` on the page: files and folders to open, a file to save, and a message, as the
  platform draws them, through the portal of the desktop inside a Linux sandbox.
- **Links go to the browser.** A link to another site, `target="_blank"`, and `window.open` open in
  the browser of the system rather than in the window, or wherever `externalLinkHandler` says;
  `Application.openExternal(url)` does the same from Java.
- **Your own title bar.** `decorated(false)` takes the title bar away and keeps the shadow and the
  resize edges; `data-lwjwae-drag` on the page's own bar moves the window, and
  `lwjwae.window.minimize()` and the like work its buttons. `closable`, `minimizable`, and
  `maximizable` take a button off a native title bar.
- **A tray icon, and windows that hide.** `Application.tray(TrayIcon)` puts an icon with a menu in
  the notification area on Windows, the menu bar on macOS, or the StatusNotifier or XEmbed tray on
  Linux. With `CloseAction.HIDE`, the close button hides a window instead of closing it while a tray
  icon is up to bring it back, and `run` keeps going while a tray icon is up, so an application can
  live in the tray.
- **Notifications.** `Application.showNotification(Notification)` shows a desktop notification with
  a title, a body, an image, and buttons, and runs a handler on a click: a toast on Windows,
  `UNUserNotificationCenter` on macOS for an `.app` bundle, and the `org.freedesktop.Notifications`
  service on Linux.
- **One instance.** `Application.createSingleInstance(parameters, args)` lets a second start hand
  its arguments to the running process, whose window comes to the front, and end, having opened
  nothing; `onSecondInstance` hears of it.
- **Pages from the classpath.** `loadResource("app/index.html")` serves the files of the
  application under a custom scheme, so relative links, stylesheets, scripts, and `fetch` resolve
  as on a web server. During development, `LWJWAE_DEV_SERVER_URL` points every window at a Vite
  or webpack server with hot reload instead.
- **Native image ready.** Every FFM stub is recorded, and each backend ships the reachability
  metadata that `native-image` needs. A test keeps the metadata in step with the bindings.

## Modules

| Module                             | Contents                                                                                                    |
|------------------------------------|-------------------------------------------------------------------------------------------------------------|
| [`lwjwae-core`](lwjwae-core)       | The API, the bridge, backend discovery, FFM helpers, and the contract tests that every backend runs.        |
| [`lwjwae-glib`](lwjwae-glib)       | What the two Linux backends share: GLib, GIO and D-Bus bindings, the GLib UI thread, notifications, a tray. |
| [`lwjwae-gtk`](lwjwae-gtk)         | The Linux backend.                                                                                          |
| [`lwjwae-gtk4`](lwjwae-gtk4)       | The Linux backend on GTK 4 and WebKitGTK 6.0. Serves its tray over D-Bus itself; can't place windows.       |
| [`lwjwae-windows`](lwjwae-windows) | The Windows backend. Finds the WebView2 runtime without `WebView2Loader.dll` and talks COM through vtables. |
| [`lwjwae-macos`](lwjwae-macos)     | The macOS backend. Drives Cocoa through the Objective-C runtime.                                            |

Each module has a README that walks through what happens on its platform: the UI thread, window
creation, callbacks, resources, script evaluation, and closing.

Two sibling repositories complete the picture:

- [lwjwae-codecs](https://github.com/fakeivchenko/lwjwae-codecs): the codec modules for the typed bridge.
- [lwjwae-examples](https://github.com/fakeivchenko/lwjwae-examples): runnable applications, starting with the demo.

## Design in short

- **One UI thread per process.** A `UiDispatcher` owns the thread that the toolkit accepts and
  forwards work to it. GTK and Win32 get a thread of their own; Cocoa gets the main thread, which
  the library doesn't own and therefore only borrows. Applications and windows share it.
- **An application is the process, a window is a window.** `Application` holds what exists once:
  the dispatcher, the codec, the WebView2 environment, the window list. `Window` holds one native
  window and its web view. Closing the last window ends `run()`; it doesn't end the application.
- **Static callbacks, keyed by ID.** A C callback carries no closure, only a `void*`. Objects are
  registered under generated IDs that travel as that pointer, so one upcall stub serves every
  window and the set of stubs stays fixed for `native-image`.
- **Nothing unwinds into C.** Every callback catches `Throwable` and reports it. A Java exception
  crossing into a toolkit is undefined behavior.
- **The bridge is one protocol, RPC.** A binding, an event from the page, and a window that the
  page opens are calls; the events of Java travel in the answer of one call that the page keeps
  open. Small calls take the message channel of the engine, `lwjwae.call` the transport that
  streams best. Only a document of the application's origin, or of the development server, may
  call.
- **A codec brings both halves.** The Java half encodes and decodes objects; the page half is a
  JavaScript object that the codec ships and the bridge runtime evaluates in every document.

## Build and test

```bash
./gradlew check            # Style checks, unit tests, and the display tests of every backend
./gradlew test             # Headless only
./gradlew displayTest      # Opens real windows; skips without a display
./gradlew networkTest      # Loads a public site; never part of a default run
./gradlew spotlessApply    # Formats every Java file
```

The display tests skip when no display is present, so a headless machine gets a passing build. CI
runs them under Xvfb with openbox as the window manager, through
`scripts/linux/with-window-manager.sh`, and with `-Dlwjwae.requireDisplay=true`, which turns a
missing display into a failure. `scripts/linux/test-in-docker.sh` runs the same environment
locally. The Windows backend compiles and runs its headless tests anywhere; `scripts/windows` drives
a Windows VM over SSH for the rest.

The code follows Google Java Style with a few additions; [docs/CODE_STYLE.md](docs/CODE_STYLE.md)
lists every rule, the tool that enforces it, and the workflow.

`scripts/linux/test-in-docker.sh` runs Gradle in the environment of the Linux CI job, from
`Dockerfile.test`: Ubuntu 24.04 with GTK 3 and 4, a virtual X display, a private session bus with a
notification server, and software rendering. Without arguments it runs every display test, with
screenshots; the reports come back to `build/docker/`:

```bash
scripts/linux/test-in-docker.sh
scripts/linux/test-in-docker.sh check
scripts/linux/test-in-docker.sh :lwjwae-gtk4:displayTest --tests '*Bridge*'
```

## CI and releases

Two workflows, two branches:

- `tests.yml` runs on every push to `dev`: the style checks first, then the headless and display
  tests on Linux (x86_64 and arm64), Windows, and macOS (x86_64 and arm64), with the screenshots of every window as
  artifacts.
- `release.yml` runs on every push to `release`: the same tests, then a version from the commit
  messages, `publish` to the Maven repository with that version, and a tag and a GitHub release.

Nothing runs for any other branch or for a pull request.

## License

Apache License 2.0. See [LICENSE](LICENSE).
