<h1 align="center">lwjwae</h1>

<p align="center">
  <b>Desktop applications in Java, with an interface on the web stack</b><br/>
  <i>No bundled browser. No JNI. One native executable.</i>
</p>

<p align="center">
  <a href="https://github.com/fakeivchenko/lwjwae/actions/workflows/tests.yml"><img alt="Tests" src="https://github.com/fakeivchenko/lwjwae/actions/workflows/tests.yml/badge.svg?branch=dev"></a>
  <a href="https://github.com/fakeivchenko/lwjwae/actions/workflows/release.yml"><img alt="Release" src="https://github.com/fakeivchenko/lwjwae/actions/workflows/release.yml/badge.svg?branch=release"></a>
  <a href="https://repo.ivchenko.dev/#/releases/dev/ivchenko/lwjwae/lwjwae-core"><img alt="Latest release" src="https://repo.ivchenko.dev/api/badge/latest/releases/dev/ivchenko/lwjwae/lwjwae-core?color=40c14a&name=release"></a>
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-blue">
  <img alt="GraalVM native-image ready" src="https://img.shields.io/badge/GraalVM-native--image%20ready-2f6fd6">
  <a href="LICENSE"><img alt="Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-green"></a>
</p>

Build the interface in HTML, CSS, and JavaScript, write the logic in Java, and ship your application
to Windows, macOS, and Linux as one native executable. lwjwae draws the page with the web view that
the operating system already has, so you don't ship a browser with every copy of your application.

![The showcase example as a native executable on Windows 11](docs/showcase-windows.png)

## Why lwjwae

- **Small.** Your application uses WebView2, WKWebView, or WebKitGTK from the system. A native
  executable takes tens of megabytes, not hundreds.
- **Pure Java.** lwjwae talks to each platform through the Foreign Function and Memory API. You
  need no C compiler, no JNI, and no native libraries in your build.
- **Native from the start.** Every backend ships the metadata that GraalVM `native-image` needs,
  so your application compiles to one executable with no configuration of your own.
- **One API on every platform.** You write the same code for Windows, macOS, and Linux, and call
  it from any thread.
- **Ready to ship.** The [Gradle plugin](https://github.com/fakeivchenko/lwjwae-gradle-plugin)
  builds the installers of every platform and signs your updates.

## Hello, world

Java opens a window and gives the page a function:

```java
public static void main(String[] args) {
  Application.launch(
      WindowParameters.of("Notes", "app/index.html"),
      window -> window.bind("greet", name -> "Hello, " + name));
}
```

The page calls it like any other asynchronous function:

```js
const greeting = await window.greet("Ada");
```

## Everything a desktop application needs

- **Windows your way.** Open as many windows as you need, draw your own title bar, or make a window
  transparent. A window can remember its size and place between runs.
- **A two-way bridge.** Call Java from the page, send events to the page, and stream bytes from
  Java to the page. With a codec, records and objects cross the bridge as they are.
- **Native look and feel.** Use the dialogs, menus, tray icon, notifications, and taskbar progress
  of the platform.
- **The desktop at hand.** Read and write the clipboard, register global shortcuts, and receive the
  files that the user drops on a window.
- **Your own links and files.** Register a scheme such as `notes://` and the types of files that
  your application opens. A second start hands its arguments to the running instance.
- **Storage built in.** Keep data in SQLite, from Java and from the page, with no extra dependency.
- **Updates you sign.** Your application downloads and installs its new versions. The release
  manifest is signed with Ed25519, so your application installs only what your key signed.
- **Fast iteration.** Point the window at a Vite or webpack development server, and the page
  reloads as you edit it.

## Supported platforms

| Platform | Web view      | Module                             |
|----------|---------------|------------------------------------|
| Windows  | WebView2      | [`lwjwae-windows`](lwjwae-windows) |
| macOS    | WKWebView     | [`lwjwae-macos`](lwjwae-macos)     |
| Linux    | WebKitGTK 4.1 | [`lwjwae-gtk`](lwjwae-gtk)         |
| Linux    | WebKitGTK 6.0 | [`lwjwae-gtk4`](lwjwae-gtk4)       |

Every backend runs on the JVM and as a native executable. Windows 11 and macOS already have their
web view; [`lwjwae-gtk`](lwjwae-gtk#requirements) lists the packages that each Linux distribution
needs.

## Get started

You need Java 25 or later. The Gradle plugin adds the library, the backend of your platform, and
the native build. In `settings.gradle.kts`, add the repository:

```kotlin
pluginManagement {
    repositories {
        maven("https://repo.ivchenko.dev/releases")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.ivchenko.dev/releases")
    }
}
```

In `build.gradle.kts`, apply the plugin:

```kotlin
plugins {
    application
    id("dev.ivchenko.lwjwae").version("VERSION")
}

application {
    mainClass = "com.example.Main"
}

lwjwae {
    displayName = "Notes"
    packaging.all()
}
```

Then run your application, or build its installers:

```bash
./gradlew run          # Runs on the JVM
./gradlew packageAll   # Builds the installers of this platform into build/lwjwae/dist
```

The [step-by-step tutorial](https://github.com/fakeivchenko/lwjwae/wiki) on the wiki takes you
from an empty project to a packaged application with a tray icon, notifications, and native
dialogs.

## Learn more

- [Wiki](https://github.com/fakeivchenko/lwjwae/wiki): the tutorial and a guide for every feature.
- [lwjwae-examples](https://github.com/fakeivchenko/lwjwae-examples): applications that you can run
  right away.
- [lwjwae-codecs](https://github.com/fakeivchenko/lwjwae-codecs): Jackson, Gson, and JSON-B codecs
  for the typed bridge.
- The README of each module, such as [`lwjwae-core`](lwjwae-core), explains how it works inside.

## Contributing

`./gradlew check` runs the style checks and every test. The display tests open real windows;
`scripts/linux/test-in-docker.sh` runs them in the environment of the Linux CI job.
[docs/CODE_STYLE.md](docs/CODE_STYLE.md) describes the code style and the tools that enforce it.

## License

Apache License 2.0. See [LICENSE](LICENSE).
