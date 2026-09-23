# lwjwae-glib

The part of the Linux backends that doesn't depend on the toolkit: bindings to GLib, GObject, GIO,
and gdk-pixbuf, and what [`lwjwae-gtk`](../lwjwae-gtk) and [`lwjwae-gtk4`](../lwjwae-gtk4) build on
them, the UI thread that runs the GLib main context, notifications, and a tray served over D-Bus.

GTK 3 and GTK 4 can't share a process, but they share these libraries, in the same versions, so the
code here runs under either. The module has no backend provider of its own; each GTK module depends
on it as an API, so an application gets it with the backend and never names it.

## Layout

| Class | Role |
|---|---|
| [`GlibDispatcher`](src/main/java/dev/ivchenko/lwjwae/glib/GlibDispatcher.java) | The UI thread on the default GLib main context: posted work arrives through `g_idle_add`. A GTK module adds the initialization and the loop of its toolkit. |
| [`FreedesktopNotifier`](src/main/java/dev/ivchenko/lwjwae/glib/FreedesktopNotifier.java), [`FreedesktopNotification`](src/main/java/dev/ivchenko/lwjwae/glib/FreedesktopNotification.java) | Notifications, through `org.freedesktop.Notifications`. |
| [`StatusNotifierTray`](src/main/java/dev/ivchenko/lwjwae/glib/StatusNotifierTray.java) | A tray icon: a StatusNotifierItem and a dbusmenu, served over D-Bus. |
| [`binding.Glib`](src/main/java/dev/ivchenko/lwjwae/glib/binding/Glib.java) | `g_*` functions: the main loop, signals, idle sources, memory streams, errors. |
| [`binding.Dbus`](src/main/java/dev/ivchenko/lwjwae/glib/binding/Dbus.java) | GDBus: calls, signals, exported objects, and the `GVariant` values they carry. |
| [`binding.GdkPixbuf`](src/main/java/dev/ivchenko/lwjwae/glib/binding/GdkPixbuf.java) | A PNG decoded into the ARGB32 pixels of a tray icon. |
| [`binding.Signatures`](src/main/java/dev/ivchenko/lwjwae/glib/binding/Signatures.java) | Every `FunctionDescriptor` the module binds, named by shape. |

The toolkit bindings stay in the GTK modules on purpose. They hold `static final` method handles
from one library, `libwebkit2gtk-4.1` or `libwebkitgtk-6.0`, and a shared class would have to keep
its handles in instance fields, which the JIT compiler doesn't treat as constants: `invokeExact`
would no longer compile to a direct call, on the path of every window operation.

## Notifications

`Application.showNotification(Notification)` goes through a
[`FreedesktopNotifier`](src/main/java/dev/ivchenko/lwjwae/glib/FreedesktopNotifier.java), one per application, created
on the first notification. It calls `org.freedesktop.Notifications`, the interface that GNOME Shell,
Plasma, dunst, mako, and every other Linux notification server implement, over GDBus. GDBus is part
of GIO, which GTK 3 and GTK 4 both load, so notifications need no library beyond the toolkit's; libnotify would add
one for nothing the interface lacks.

The arguments of `Notify` are `GVariant` values, built one constructor at a time in
[`binding.Dbus`](src/main/java/dev/ivchenko/lwjwae/glib/binding/Dbus.java). `g_variant_new` with a
format string would be shorter, but it's variadic, and a variadic downcall needs a descriptor per
combination of arguments. The image goes as a PNG file in a temporary directory, named in the
`image-path` hint; the file is deleted when its notification is gone, and the directory with the
application.

Buttons are the actions `action-0`, `action-1`, and so on; `onActivate` is the `default` action,
which servers run on a click of the notification itself. The server reports a click with the
`ActionInvoked` signal and the end of a notification with `NotificationClosed`. The notifier
subscribes to both on the GTK thread and calls `Notify` there too, so the ID of a notification is in
its table before the loop can deliver a signal about it. Handlers run on a virtual thread, off the
GTK thread.

Without a session bus, or with nothing that owns `org.freedesktop.Notifications` on it,
`showNotification` throws `UnsupportedOperationException`. `Application.quit()` takes back every
notification still on screen with `CloseNotification`, because their handlers go with the
application.

## Tray

GTK 4 has no tray: `GtkStatusIcon` is gone, and libappindicator links GTK 3, which can't share a
process with GTK 4. Under GTK 3, libappindicator may be missing, and `GtkStatusIcon` needs X11. A
panel, though, only talks D-Bus, so
[`StatusNotifierTray`](src/main/java/dev/ivchenko/lwjwae/glib/StatusNotifierTray.java) serves the two objects that
libappindicator would have served: `/StatusNotifierItem` with `org.kde.StatusNotifierItem` for the
icon, and `/MenuBar` with `com.canonical.dbusmenu` for the menu, both through
`g_dbus_connection_register_object` with introspection XML and a vtable of two upcall stubs. It
registers the item with `org.kde.StatusNotifierWatcher`, which KDE, GNOME with the AppIndicator
extension, and most other panels run. Without a watcher, the constructor throws
`UnsupportedOperationException`. The GTK 4 backend uses it as its tray; the GTK 3 backend uses it
where libappindicator is missing, before it falls back to `GtkStatusIcon`.

- **One connection per tray.** The watcher tracks an item by the unique bus name that registered it,
  and drops the item when that name leaves the bus. On the shared session connection, the name lives
  as long as the process, so a closed tray would stay on the panel; each tray opens a connection of
  its own with `g_dbus_connection_new_for_address_sync`, and `close()` closes it.
- **The icon as pixels.** `IconName` is empty and `IconPixmap` carries the image. A name is looked
  up in the icon theme first, and a theme without it falls back by cutting the name at dashes: KDE
  drew a file named `image-1` as the generic `image` icon. gdk-pixbuf, which both toolkits depend
  on, decodes the PNG, and the samples are reordered into the ARGB32 in network byte order of the
  specification.
- **Clicks.** `Activate`, the primary click, runs `onActivate`. Without it, `ItemIsMenu` is true, and
  the panel opens the menu on any click. The menu is flat: the root has the ID 0 and each entry its
  position plus one, with `label` and `enabled`, or `type` `separator`. A `clicked` event, alone or in
  an `EventGroup`, runs the action of an enabled entry. `menu(...)` bumps the revision and emits
  `LayoutUpdated`; `icon(...)` and `tooltip(...)` emit `NewIcon`, `NewToolTip`, and `NewTitle`.

The objects are registered on the GTK thread, so their method calls and property reads arrive
there, and handlers run on a virtual thread, as everywhere. The call that registers the item with
the watcher is made from the calling thread instead: the watcher reads the properties of the item
before it answers, and a GTK thread blocked in that call couldn't serve them.

## Native image

The module ships the reachability metadata of its own stubs. `native-image` merges it with the
metadata of the GTK module on the class path, and the metadata test of each GTK module checks the
two together, through `inheritedMetadataPaths()`.

## Tests

```bash
./gradlew :lwjwae-glib:test   # Headless: reachability metadata
```

The notifications and the tray run in the display tests of the GTK modules, under a real toolkit.
The test fixtures carry a `FakeStatusNotifierWatcher`, which the tray tests start on a session bus
that has no watcher, such as the one on CI.
