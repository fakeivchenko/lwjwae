# lwjwae-macos

The macOS backend: an `NSWindow` with a `WKWebView`, driven through the Objective-C runtime.

## Requirements

- macOS, arm64 or x86_64. AppKit and WebKit are part of the system, so the platform check is the
  whole test.

The provider [`MacBackendProvider`](src/main/java/dev/ivchenko/lwjwae/macos/MacBackendProvider.java) registers under the name `cocoa-wkwebview`.

## Layout

| Class                                                                                   | Role                                                                          |
|-----------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| [`MacApplication`](src/main/java/dev/ivchenko/lwjwae/macos/MacApplication.java)         | The application. Runs the loop from the main thread of a native image.        |
| [`MacWindow`](src/main/java/dev/ivchenko/lwjwae/macos/MacWindow.java)                   | The window. Forwards every call to the main thread.                           |
| [`MacRpcExchange`](src/main/java/dev/ivchenko/lwjwae/macos/MacRpcExchange.java) | One RPC call over a `WKURLSchemeTask`. |
| [`MacDispatcher`](src/main/java/dev/ivchenko/lwjwae/macos/MacDispatcher.java)           | The main thread of the process, and how work reaches it.                      |
| [`MacMainMenu`](src/main/java/dev/ivchenko/lwjwae/macos/MacMainMenu.java) | The menu bar, with the shortcuts of editing, and Quit as `Application.quit()`. |
| [`MacTray`](src/main/java/dev/ivchenko/lwjwae/macos/MacTray.java)                       | A tray icon: an `NSStatusItem` in the menu bar.                               |
| [`MacNotifier`](src/main/java/dev/ivchenko/lwjwae/macos/MacNotifier.java), [`MacNotification`](src/main/java/dev/ivchenko/lwjwae/macos/MacNotification.java) | Notifications, through `UNUserNotificationCenter`. |
| [`PendingEvaluation`](src/main/java/dev/ivchenko/lwjwae/macos/PendingEvaluation.java)   | A future and the arena of its completion block.                               |
| [`binding.ObjC`](src/main/java/dev/ivchenko/lwjwae/macos/binding/ObjC.java)             | The runtime: classes, selectors, `objc_msgSend`, blocks, autorelease pools.   |
| [`binding.Foundation`](src/main/java/dev/ivchenko/lwjwae/macos/binding/Foundation.java) | Strings, URLs, data, errors, geometry structs.                                |
| [`binding.AppKit`](src/main/java/dev/ivchenko/lwjwae/macos/binding/AppKit.java)         | The application object, windows, the status bar, and menus.                   |
| [`binding.UserNotifications`](src/main/java/dev/ivchenko/lwjwae/macos/binding/UserNotifications.java) | The UserNotifications framework: the center, requests, categories, and actions. |
| [`binding.WebKit`](src/main/java/dev/ivchenko/lwjwae/macos/binding/WebKit.java)         | The view, its configuration, user scripts, messages, scheme tasks.            |
| [`binding.MethodStub`](src/main/java/dev/ivchenko/lwjwae/macos/binding/MethodStub.java) | One method of a class defined at runtime.                                     |
| [`binding.Signatures`](src/main/java/dev/ivchenko/lwjwae/macos/binding/Signatures.java) | Every `FunctionDescriptor` the module binds, mostly shapes of `objc_msgSend`. |

## Talking to Objective-C

Every call to Cocoa is a message send. `objc_msgSend` is a trampoline that takes whatever the target
method takes, so [`ObjC`](src/main/java/dev/ivchenko/lwjwae/macos/binding/ObjC.java) binds one downcall handle per distinct signature and names the shape:
`send` returns `id`, `sendVoid` returns nothing, `sendLong` returns `NSInteger`, and so on, each
with overloads for the arguments. Classes and selectors are looked up once and cached, so a send
costs one hash lookup and one native call.

No method that returns a struct is bound. On x86_64, such a call must go through
`objc_msgSend_stret`, which doesn't exist on arm64. Where a struct is needed, the backend reads it
through key-value coding: `valueForKey:` boxes it into an `NSValue`, and `getValue:size:` copies it
out.

Callbacks go the other way. [`ObjC.defineClass`](src/main/java/dev/ivchenko/lwjwae/macos/binding/ObjC.java) creates a class at runtime with
`objc_allocateClassPair`, adds one method per [`MethodStub`](src/main/java/dev/ivchenko/lwjwae/macos/binding/MethodStub.java), each an upcall stub with its
Objective-C type encoding, and registers it. Every method receives `self` and `_cmd` before its own
arguments, as Objective-C passes them.

## The main thread

AppKit accepts only the main thread of the process, and the library doesn't own it. There are two
situations, and [`MacDispatcher`](src/main/java/dev/ivchenko/lwjwae/macos/MacDispatcher.java) handles both:

- Under the `java` launcher, the main thread is parked in a `CFRunLoop` while Java code runs on
  another thread. `post` queues the task and calls
  `performSelectorOnMainThread:withObject:waitUntilDone:NO` on a `LwjwaeDispatcher` object whose
  `drain` method is an upcall stub. That's a run loop source, so it keeps firing after the first
  batch has started `-[NSApplication run]` and that nested loop owns the thread for the rest of the
  process.
- In a native image, the `main` method of the application *is* the main thread. Calls made from it
  run inline, and [`MacApplication.run()`](src/main/java/dev/ivchenko/lwjwae/macos/MacApplication.java) starts the application loop itself.

On first use, [`MacDispatcher.instance()`](src/main/java/dev/ivchenko/lwjwae/macos/MacDispatcher.java) creates the shared `NSApplication` with the regular
activation policy (a Dock icon and a menu bar). When it's already on the main thread, it calls
`finishLaunching`, because WebKit starts its helper processes only in an application that has
launched. Otherwise, it posts `sharedApplication` and then `run`.

Every task runs inside an autorelease pool: `execute` pushes one before and pops it after.

## Creating a window

`new MacApplication(parameters)` only makes sure that the dispatcher, and with it `NSApplication`,
exists. `application.open(parameters)` runs the following on the main thread and returns when it's
done:

1. Allocates an instance of `LwjwaeDelegate`, the class defined once per process with the window
   delegate, navigation delegate, script message handler, and URL scheme handler methods. Records
   the window under the address of the delegate; that address is the key from every callback back
   to the window.
2. Creates a `WKWebViewConfiguration`, sets the delegate as the handler for the `app` URL scheme,
   retains the `WKUserContentController` of the configuration, and adds the delegate as the
   script message handler named `__lwjwaeBridge`.
3. Creates the `WKWebView` with that configuration, with an autoresizing mask that follows the
   window, and sets the delegate as its navigation delegate.
4. Creates an `NSWindow` with the title, closable, miniaturizable, and resizable style, and with
   `releasedWhenClosed` off, so the backend owns its lifetime. Centers it, then moves it to the
   requested position if there is one. Sets the view as the content view and the delegate as the
   window delegate. `position()` reads `frame` through key-value coding and flips the Y axis with
   the height of the primary screen, because AppKit measures from the bottom left; `position(x, y)`
   converts the same way and sends `setFrameOrigin:`, whose `NSPoint` has the layout of an
   `NSSize`; `center()` is `-[NSWindow center]`.
5. Injects a user script that cancels `contextmenu` unless `window.__lwjwaeContextMenu` is set.
   WKWebView has no setting to suppress its menu, and a shipped application doesn't want "Reload"
   and "Inspect Element" in it.
6. Calls `installBridge()`.

The window stays hidden until `show()`, which calls `makeKeyAndOrderFront:` and activates the
application.

## Callbacks

Every delegate method looks the window up by `self`, does the work, catches `Throwable` and
reports it. Nothing unwinds into Cocoa.

| Selector                                                                                  | Handler                           | What it does                                                                                                                                          |
|-------------------------------------------------------------------------------------------|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `webView:didStartProvisionalNavigation:`                                                  | `onDidStartProvisionalNavigation` | Emits `STARTED` with the current URL.                                                                                                                 |
| `webView:didCommitNavigation:`                                                            | `onDidCommitNavigation`           | Emits `COMMITTED`.                                                                                                                                    |
| `webView:didFinishNavigation:`                                                            | `onDidFinishNavigation`           | Emits `FINISHED`.                                                                                                                                     |
| `webView:didFailProvisionalNavigation:withError:`, `webView:didFailNavigation:withError:` | `onDidFailNavigation`             | Emits `LoadEvent.failed` once per URL, with the failing URL from the `NSError` and its localized description.                                         |
| `userContentController:didReceiveScriptMessage:`                                          | `onDidReceiveScriptMessage`       | Reads the body of the `WKScriptMessage` and calls `handleBridgeMessage`.                                                                              |
| `webView:startURLSchemeTask:`                                                             | `onStartUrlSchemeTask`            | Serves the resource, described next.                                                                                                                  |
| `webView:stopURLSchemeTask:`                                                              | `onStopUrlSchemeTask`             | Nothing: a resource is answered in one step, so there's nothing to stop.                                                                              |
| `windowWillClose:`                                                                        | `onWindowWillClose`               | Detaches the delegates, releases every object, and calls `markClosed()`, which stops the run loop if `run()` started it and this was the last window. |

## Serving resources

The `app://local/PATH` scheme is answered per window through `WKURLSchemeHandler`:

1. Reads the URL of the `WKURLSchemeTask`, strips the scheme, the host, and the query string.
2. Reads the bytes from the classpath.
3. Builds an `NSURLResponse` with the URL, the media type from [`MimeTypeUtil`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/util/MimeTypeUtil.java), and the length;
   calls `didReceiveResponse:`, `didReceiveData:` with an `NSData` copy, and `didFinish`.
4. On [`ResourceNotFoundException`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/exception/ResourceNotFoundException.java), calls `didFailWithError:` with an `NSError` of code 404 in the
   `dev.ivchenko.lwjwae` domain. WebKit reports that to the navigation delegate as a failed
   provisional navigation. When the missing resource is the document being loaded, the backend also
   emits [`LoadEvent.failed`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/event/LoadEvent.java) itself and remembers the URL, so the delegate doesn't report it twice.

## RPC

A `POST` under `app://local/__lwjwae/rpc/` is a call, handled by [`MacRpcExchange`](src/main/java/dev/ivchenko/lwjwae/macos/MacRpcExchange.java):

1. Reads the method, the path, `Origin`, `Content-Type`, and the body (`HTTPBody`, or
   `HTTPBodyStream` read to its end) in the start callback, and retains the task.
2. Answers with `didReceiveResponse:`, one `didReceiveData:` per part, and `didFinish`, each posted
   to the main thread with at most 16 waiting.
3. `webView:stopURLSchemeTask:` marks the call cancelled and releases the task. A task must not be
   touched after it is stopped, since that raises an Objective-C exception, so every step checks on
   the main thread, where the stop also runs, whether the task is still live.

This path is compiled but not yet run on macOS.

## Window state

`miniaturize:`, `zoom:`, and `toggleFullScreen:` change the state; the last two toggle, so they're
sent only when the state differs. On top is the floating window level; focus is the key window.
`setContentMinSize:` and `setContentMaxSize:` set the limits, and a window outside them is resized
into them, since AppKit only keeps the user within them.

A window without a title bar stays titled, with `NSWindowStyleMaskFullSizeContentView`, a
transparent title bar, a hidden title, and hidden buttons, the way Electron makes a frameless
window: a borderless one would lose the rounded corners and the shadow, and couldn't become the key
window without a subclass. `closable` and `minimizable` are bits of the style mask; `maximizable`
grays out the zoom button, which `zoom:` enables for the moment it zooms. A drag region moves the
window with `performWindowDragWithEvent:` and the mouse event being handled, and a double click on
one does what `AppleActionOnDoubleClick` says.

## Evaluating scripts

`evaluateJavaScript:completionHandler:` takes a block. The backend builds one by hand:

1. Wraps the script with [`ScriptUtil.taggedEvaluation`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/util/ScriptUtil.java), the same wrapper as on Windows: the caller
   text runs in the global scope, and the outcome comes back as one string tagged `S` or `E`. WebKit
   would otherwise replace the message of a thrown error with a generic one.
2. Allocates a block literal in an automatic arena, marked global so the runtime never copies or
   frees it, with the shared completion stub as `invoke` and the ID of a [`PendingEvaluation`](src/main/java/dev/ivchenko/lwjwae/macos/PendingEvaluation.java) as its
   one captured value.
3. Posts the evaluation to the main thread.
4. In the completion, the stub reads the ID back from the block, looks the pending evaluation up,
   and completes its future: an `NSError` becomes [`ScriptEvaluationFailedException`](../lwjwae-core/src/main/java/dev/ivchenko/lwjwae/exception/ScriptEvaluationFailedException.java) with its
   description, and a result goes through `ScriptUtil.completeTagged`.

## Developer tools

`devToolsEnabled(true)` sets `developerExtrasEnabled` on the `WKPreferences` of the view through
key-value coding. The key is private but has been stable since Safari 9, and it's what every
embedding application uses. That enables the Web Inspector and adds "Inspect Element" to the
context menu, so the backend also sets `window.__lwjwaeContextMenu` in the current document and in
every later one, which lets the menu through. `isDevToolsEnabled()` reads the preference back.

## Running the application loop

`MacApplication.run()` has two behaviors, chosen by where it's called from:

- From any thread other than the main thread, or when the application loop is already running:
  blocks while any window is open, like the other backends.
- From the main thread before the loop started, which is the `main` method of a native image:
  calls `-[NSApplication run]` itself, unless no window is open, in which case it returns at once.
  When the last window closes, or on `quit()`, `onIdle()` calls `stop:` and posts an
  application-defined event, because `stop:` takes effect only after the loop processes an event.
  `run` returns, and so does `run()`.

## Tray

`Application.tray(TrayIcon)` creates a [`MacTray`](src/main/java/dev/ivchenko/lwjwae/macos/MacTray.java): an
`NSStatusItem` on the system status bar, with the image scaled to 18 points and the tooltip on its
button. AppKit reports clicks as actions sent to a target, so each tray has a target object of its
own, of a class defined at runtime, `LwjwaeTrayTarget`, whose two methods are upcall stubs: one for
the button, one for the menu entries. Each entry carries its position plus one as its tag.

Without `onActivate`, the menu belongs to the status item, and AppKit opens it on any click, as it
does for every other menu bar extra. With a handler, the button sends its action on a release of
either mouse button: a primary click runs the handler, and a secondary or Control click lends the
menu to the status item for one `performClick:`, which opens the menu and returns when it closes.
Entry actions and `onActivate` run on a virtual thread, off the main thread.

The tray closes with its application, on `quit()`, or earlier through `Tray.close()`. Until then it
keeps `Application.run()` going, so an application can live in the menu bar with no window open.

## Notifications

`Application.showNotification(Notification)` goes through a
[`MacNotifier`](src/main/java/dev/ivchenko/lwjwae/macos/MacNotifier.java), which uses
`UNUserNotificationCenter`. The center exists only for an application bundle: asked for in a process
that runs from the `java` launcher or as a bare executable, it raises an Objective-C exception that
ends the process, so the notifier checks that the main bundle is an `.app` with an identifier first,
and otherwise throws `UnsupportedOperationException`. An application that shows notifications is
packaged as an `.app`.

The first notification asks the user whether the application may show notifications, through
`requestAuthorizationWithOptions:completionHandler:`, and waits for the answer; after a no, every
notification is refused with `UnsupportedOperationException` until the user turns them on in System
Settings. `showNotification` also waits for the completion block of `addNotificationRequest:`, so a
notification that macOS refuses is an exception, not silence.

Buttons are the actions of a category, one category per notification, with the identifiers
`action-0`, `action-1`, and so on. The center keeps one set of categories for the whole
application, so the notifier sets it again whenever a notification comes or goes. Each category
asks for dismissals to be reported. A delegate, `LwjwaeNotificationDelegate`, defined at runtime
with upcall stubs, receives the clicks and the dismissals in
`userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:`, and answers
`willPresentNotification:` with a banner: without that, macOS doesn't show the notification of an
application that is in front. The completion blocks that the center passes to the delegate are
called through their invoke pointer.

The display tests run the contract only where the process is a bundle whose notifications are
allowed; the test JVM on CI is neither, and there the tests check that the refusal is clean.

## Dialogs

`NSOpenPanel`, `NSSavePanel`, and `NSAlert` are sheets of the window, begun with
`beginSheetModalForWindow:completionHandler:`, which returns at once and calls the block when the
user answers: `runModal` would hold up the work of other threads until then. The block is a
global literal like the one of `evaluateJavaScript:`, and a cancellation ends the sheet with
`endSheet:`, which calls it too. A panel has no menu of kinds of file, so it takes the extensions of
every kind through `setAllowedFileTypes:`, and the title of the dialog goes to `setMessage:`, since
a sheet has no title bar.

## The menu bar

On macOS, the shortcuts of editing belong to the menu bar: Command-C is the key equivalent of an
item that sends `copy:` to the first responder, and a `WKWebView` hands back to the menu every key
that the page leaves. Without a menu bar, a text field takes no Command-C, V, X, A, or Z. The first
application installs the menu bar of every Mac application, in place of the bare one that AppKit
makes up when `run` starts without one: the
application menu (About, Hide, Hide Others, Show All, Quit), File (Close Window), Edit (Undo, Redo,
Cut, Copy, Paste, Paste and Match Style, Delete, Select All), and Window (Minimize, Zoom, Bring All
to Front, and the list of windows). The items have no target, so each goes along the responder
chain, which also enables it. The titles take `ApplicationParameters.name()`, or the name of the
process without one.

Quit, from the menu, the Dock, or a logout, is `terminate:`, which asks the delegate of
`NSApplication` `applicationShouldTerminate:` and then calls `exit` under the JVM. The delegate
answers `NSTerminateCancel` and quits every open application instead, so their windows close as on
`Application.quit()`, `run()` returns, and the program ends on its own terms. With no application
open, it lets AppKit terminate.

## Closing

`close()` calls `-[NSWindow close]` on the main thread. The delegate receives `windowWillClose:`
synchronously, so the cleanup runs before `close()` returns, and the last window to close releases
every thread blocked in `Application.run()`. Closing the window with the red button takes the same
path. `close()` is idempotent. `Application.quit()` closes every window this way; `NSApplication`
stays, and another application can be created on it.

## Tests

```bash
./gradlew :lwjwae-macos:test          # Headless: provider registration; metadata on macOS
./gradlew :lwjwae-macos:displayTest   # Opens real windows; macOS only
```

With `-Dlwjwae.screenshots=true`, the display tests capture the screen with the `screencapture`
tool of the system, as every platform does with its own tool: AWT would also bring a second
`NSApplication` into a process that already runs one.
