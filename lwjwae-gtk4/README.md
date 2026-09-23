# lwjwae-gtk4

The Linux backend on the current toolkit: a GTK 4 window with a WebKitGTK 6.0 view.

It covers what the GTK 3 backend does: windows, pages from the classpath under `app://`, the
bridge in both directions, script evaluation, load events, the developer tools, hiding and closing
windows, the tray, and notifications. What it can't do is place a window: GTK 4 has no API for that
on any display server.

## Requirements

- Linux or a BSD with a desktop.
- `libgtk-4.so.1` and `libwebkitgtk-6.0.so.4` on the loader path, that is, GTK 4 and WebKitGTK 2.40
  or newer with the 6.0 API.
- An X11 or Wayland display.

| Distribution                       | Packages                             |
|------------------------------------|--------------------------------------|
| Debian 12+, Ubuntu 23.04+          | `libgtk-4-1 libwebkitgtk-6.0-4`      |
| Fedora 38+, RHEL 10 and derivatives | `gtk4 webkitgtk6.0`                 |
| Arch                               | `gtk4 webkitgtk-6.0`                 |
| openSUSE                           | `libgtk-4-1 libwebkitgtk-6_0-4`      |
| Alpine                             | `gtk4.0 webkit2gtk-6.0`              |

The provider [`Gtk4BackendProvider`](src/main/java/dev/ivchenko/lwjwae/gtk4/Gtk4BackendProvider.java)
registers under the name `gtk4-webkitgtk-6.0`, with a priority below the GTK 3 backend: when both
modules are on the classpath and both libraries are installed, GTK 3 runs, as it can also place
windows on X11, and GTK 4 runs where it is the only one, as on a distribution that ships WebKitGTK 6.0
without 4.1. `-Dlwjwae.backend=gtk4-webkitgtk-6.0`, or `LWJWAE_BACKEND`, picks it anyway.

GTK 3 and GTK 4 can't share a process, but only one backend ever starts its toolkit: the other
provider only probes its libraries, which loads them without initializing anything.

## Layout

| Class                                                                                        | Role                                                                               |
|----------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [`Gtk4Application`](src/main/java/dev/ivchenko/lwjwae/gtk4/Gtk4Application.java)             | The application. Prepares the web context and serves `app://`; opens windows.      |
| [`Gtk4Window`](src/main/java/dev/ivchenko/lwjwae/gtk4/Gtk4Window.java)                       | The window. Forwards every call to the GTK thread.                                 |
| [`Gtk4Dispatcher`](src/main/java/dev/ivchenko/lwjwae/gtk4/Gtk4Dispatcher.java)               | The one GTK thread of the process, running a `GMainLoop`.                          |
| [`Gtk4Tray`](src/main/java/dev/ivchenko/lwjwae/gtk4/Gtk4Tray.java)                           | A tray icon: a StatusNotifierItem and a dbusmenu, served over D-Bus.               |
| [`Gtk4Notifier`](src/main/java/dev/ivchenko/lwjwae/gtk4/Gtk4Notifier.java), [`Gtk4Notification`](src/main/java/dev/ivchenko/lwjwae/gtk4/Gtk4Notification.java) | Notifications, through `org.freedesktop.Notifications`. |
| [`binding.Gtk`](src/main/java/dev/ivchenko/lwjwae/gtk4/binding/Gtk.java)                     | `gtk_*` functions: the window.                                                     |
| [`binding.WebKit`](src/main/java/dev/ivchenko/lwjwae/gtk4/binding/WebKit.java)               | `webkit_*` functions: the view, user scripts, message handlers, the URI scheme.    |
| [`binding.Glib`](src/main/java/dev/ivchenko/lwjwae/gtk4/binding/Glib.java)                   | `g_*` functions: the main loop, signals, idle sources, memory streams, errors.     |
| [`binding.Gdk`](src/main/java/dev/ivchenko/lwjwae/gtk4/binding/Gdk.java)                     | `GdkTexture`: a PNG decoded into the ARGB32 pixels of a tray icon.                 |
| [`binding.Dbus`](src/main/java/dev/ivchenko/lwjwae/gtk4/binding/Dbus.java)                   | GDBus: calls, signals, exported objects, and the `GVariant` values they carry.     |
| [`binding.Signatures`](src/main/java/dev/ivchenko/lwjwae/gtk4/binding/Signatures.java)       | Every `FunctionDescriptor` the module binds, named by shape.                       |

## What changed from GTK 3

The backend follows [`lwjwae-gtk`](../lwjwae-gtk) closely; these are the places where GTK 4 and
WebKitGTK 6.0 differ, and what the backend does about each.

- **The main loop.** GTK 4 has no `gtk_main()`. The GTK thread runs a `GMainLoop` on the default
  main context, which is what `gtk_main()` did, and work still arrives through `g_idle_add`.
- **Windows.** `gtk_window_new()` takes no type, the web view goes in with `gtk_window_set_child`, a
  window shows with `gtk_window_present` and closes for good with `gtk_window_destroy`, which emits
  `destroy` synchronously as `gtk_widget_destroy` did. The title-bar close button emits
  `close-request` instead of `delete-event`; `TRUE` cancels the close, as before, which is how
  `CloseAction.HIDE` hides the window.
- **Size.** There is no `gtk_window_resize`: `gtk_window_set_default_size` also resizes a window that
  is on screen, and GTK 4 keeps the default size in step with the size of the window, so it is also
  what `width()` and `height()` read.
- **Placement.** GTK 4 can't move a window or tell where it is, on X11 as on Wayland. The position in
  `WindowParameters` is ignored, `position()` answers `0, 0`, and `position(x, y)` and `center()` do
  nothing: what the API promises for Wayland, everywhere.
- **The web view.** A view is created plain with `webkit_web_view_new` and comes with a user content
  manager of its own, from `webkit_web_view_get_user_content_manager`;
  `webkit_web_view_new_with_user_content_manager` is gone, and the replacement, `g_object_new` with
  a property, is variadic. A script message handler is registered for a script world, `NULL` for
  the default one, and `script-message-received` hands over the `JSCValue` itself instead of a
  `WebKitJavascriptResult`. `context-menu` lost its `GdkEvent*` argument.
- **Exit.** WebKit drops its own references to the default web context and to the default network
  session, new in 6.0, from an `atexit` handler. With no web view left by then, either one is
  disposed, and WebKit aborts: every JVM that used a web view would end in a core dump. The
  application takes one permanent reference to each, as the GTK 3 backend does for the context.

## Tray

GTK 4 has no tray. `GtkStatusIcon` is gone, and libappindicator, which the GTK 3 backend uses,
links GTK 3, which can't share a process with GTK 4. A panel, though, only talks D-Bus, so
[`Gtk4Tray`](src/main/java/dev/ivchenko/lwjwae/gtk4/Gtk4Tray.java) serves the two objects that
libappindicator would have served: `/StatusNotifierItem` with `org.kde.StatusNotifierItem` for the
icon, and `/MenuBar` with `com.canonical.dbusmenu` for the menu, both through
`g_dbus_connection_register_object` with introspection XML and a vtable of two upcall stubs. It
registers the item with `org.kde.StatusNotifierWatcher`, which KDE, GNOME with the AppIndicator
extension, and most other panels run. Without a watcher, `Application.tray` throws
`UnsupportedOperationException`.

- **One connection per tray.** The watcher tracks an item by the unique bus name that registered it,
  and drops the item when that name leaves the bus. On the shared session connection, the name lives
  as long as the process, so a closed tray would stay on the panel; each tray opens a connection of
  its own with `g_dbus_connection_new_for_address_sync`, and `close()` closes it.
- **The icon as pixels.** `IconName` is empty and `IconPixmap` carries the image. A name is looked
  up in the icon theme first, and a theme without it falls back by cutting the name at dashes: KDE
  drew a file named `image-1` as the generic `image` icon. `GdkTexture` decodes the PNG, and the
  pixels are turned from Cairo's native, premultiplied ARGB32 into the straight ARGB32 in network
  byte order of the specification.
- **Clicks.** `Activate`, the primary click, runs `onActivate`. Without it, `ItemIsMenu` is true, and
  the panel opens the menu on any click. The menu is flat: the root has the ID 0 and each entry its
  position plus one, with `label` and `enabled`, or `type` `separator`. A `clicked` event, alone or in
  an `EventGroup`, runs the action of an enabled entry. `menu(...)` bumps the revision and emits
  `LayoutUpdated`; `icon(...)` and `tooltip(...)` emit `NewIcon`, `NewToolTip`, and `NewTitle`.

The objects are registered on the GTK thread, so their method calls and property reads arrive
there, and handlers run on a virtual thread, as everywhere.

## Notifications

The same as in [`lwjwae-gtk`](../lwjwae-gtk#notifications): `org.freedesktop.Notifications` over
GDBus, which doesn't depend on the version of GTK.

## Tests

```bash
./gradlew :lwjwae-gtk4:test          # Headless: provider registration, reachability metadata
./gradlew :lwjwae-gtk4:displayTest   # Opens real windows; skips without a display
./gradlew :lwjwae-gtk4:networkTest   # Loads google.com; never part of a default run
```

The display tests are the contract tests of every backend; the placement test only checks that the
calls return. The tray tests need a StatusNotifier host, and the notification tests a notification
server, on the session bus.
