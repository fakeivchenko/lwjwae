package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.MethodStub;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.macos.binding.Signatures;
import dev.ivchenko.lwjwae.macos.binding.UserNotifications;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The notifications of one application, through {@code UNUserNotificationCenter}.
 *
 * <p>The center exists only for an application bundle, so the notifier refuses, with {@link
 * UnsupportedOperationException}, in any other process: run from the {@code java} launcher, or as a
 * bare executable, the center would raise an Objective-C exception that ends the process.
 *
 * <p>macOS asks the user once whether the application may show notifications. The first
 * notification waits for that answer, and every one after it goes out at once, or is refused at
 * once when the answer was no. {@code addNotificationRequest:} reports its outcome to a completion
 * block, and {@link #show} waits for that too, so a refused notification is an exception rather
 * than silence.
 *
 * <p>Buttons come from a category, which the center keeps as one set for the whole application;
 * each notification gets a category of its own, and the notifier sets the whole set again whenever
 * one is added or removed. Each category asks for the dismissal to be reported, which is how a
 * handle learns that the user closed its notification. Clicks and dismissals reach a delegate, an
 * object of a class defined at runtime whose methods are upcall stubs. The delegate also answers
 * {@code willPresentNotification:}, because otherwise macOS doesn't show the notification of an
 * application that is in front, which is exactly when a desktop application sends most of them.
 */
final class MacNotifier {
  private static final String BUTTON_ACTION_PREFIX = "action-";
  private static final long AUTHORIZATION_TIMEOUT_SECONDS = 120;
  private static final long REQUEST_TIMEOUT_SECONDS = 10;

  private static final Map<Long, MacNotifier> DELEGATES = new ConcurrentHashMap<>();
  private static final CallbackRegistry<PendingCompletion<Optional<String>>> PENDING_REQUESTS =
      new CallbackRegistry<>();
  private static final CallbackRegistry<PendingCompletion<Boolean>> PENDING_AUTHORIZATIONS =
      new CallbackRegistry<>();
  private static final AtomicLong IDS = new AtomicLong();

  private static final MemorySegment ON_REQUEST_ADDED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacNotifier.class,
          "onRequestAdded",
          MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
          Signatures.ERROR_BLOCK);
  private static final MemorySegment ON_AUTHORIZED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacNotifier.class,
          "onAuthorized",
          MethodType.methodType(
              void.class, MemorySegment.class, boolean.class, MemorySegment.class),
          Signatures.AUTHORIZATION_BLOCK);
  private static final MemorySegment ON_WILL_PRESENT = delegateStub("onWillPresent");
  private static final MemorySegment ON_DID_RECEIVE_RESPONSE = delegateStub("onDidReceiveResponse");

  private static final MemorySegment DELEGATE_CLASS =
      ObjC.defineClass(
          "LwjwaeNotificationDelegate",
          ObjC.cls("NSObject"),
          Map.of(
              "userNotificationCenter:willPresentNotification:withCompletionHandler:",
              new MethodStub(ON_WILL_PRESENT, "v@:@@@?"),
              "userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:",
              new MethodStub(ON_DID_RECEIVE_RESPONSE, "v@:@@@?")));

  private final UiDispatcher dispatcher;
  private final Map<String, MacNotification> shown = new ConcurrentHashMap<>();
  private final Map<String, MemorySegment> categories = new ConcurrentHashMap<>();

  private volatile MemorySegment center;
  private volatile MemorySegment delegate;
  private volatile boolean authorized;
  private volatile Path directory;

  /**
   * Takes the center of the application and becomes its delegate.
   *
   * @throws UnsupportedOperationException If the process isn't an application bundle.
   */
  MacNotifier(UiDispatcher dispatcher) {
    this.dispatcher = dispatcher;
    this.dispatcher.run(
        () -> {
          if (!UserNotifications.isBundledApplication()) {
            throw new UnsupportedOperationException(
                "macOS shows notifications only for an application bundle (.app), and this process"
                    + " isn't one");
          }
          MemorySegment newDelegate = ObjC.send(ObjC.send(DELEGATE_CLASS, "alloc"), "init");
          DELEGATES.put(newDelegate.address(), this);
          MemorySegment newCenter = UserNotifications.center();
          UserNotifications.setDelegate(newCenter, newDelegate);
          this.delegate = newDelegate;
          this.center = newCenter;
        });
  }

  /**
   * Hands {@code notification} to the center and returns once the center took it.
   *
   * @throws UnsupportedOperationException If the user didn't allow notifications, or the center
   *     refused this one.
   */
  MacNotification show(Notification notification, Consumer<NotificationHandle> closed) {
    this.authorize();
    String identifier = "lwjwae-" + ProcessHandle.current().pid() + "-" + IDS.incrementAndGet();
    Path image = notification.icon() == null ? null : this.writeImage(notification.icon());
    MacNotification handle = new MacNotification(this, identifier, notification, image, closed);
    CompletableFuture<Optional<String>> added = new CompletableFuture<>();
    this.shown.put(identifier, handle);
    try {
      this.dispatcher.run(() -> this.add(identifier, notification, image, added));
      Optional<String> error = added.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (error.isPresent()) {
        throw new UnsupportedOperationException("macOS refused the notification: " + error.get());
      }
      return handle;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      this.forget(identifier);
      throw new IllegalStateException("Interrupted while macOS took the notification", e);
    } catch (ExecutionException | TimeoutException e) {
      this.forget(identifier);
      throw new UnsupportedOperationException("macOS didn't take the notification", e);
    } catch (RuntimeException e) {
      this.forget(identifier);
      throw e;
    }
  }

  /** Builds the content and the category, and adds the request. Runs on the main thread. */
  private void add(
      String identifier,
      Notification notification,
      Path image,
      CompletableFuture<Optional<String>> added) {
    List<MemorySegment> actions = new ArrayList<>();
    List<NotificationAction> buttons = notification.actions();
    for (int index = 0; index < buttons.size(); index++) {
      actions.add(
          UserNotifications.action(BUTTON_ACTION_PREFIX + index, buttons.get(index).label()));
    }
    this.categories.put(
        identifier, Foundation.retain(UserNotifications.category(identifier, actions)));
    UserNotifications.setCategories(this.center(), List.copyOf(this.categories.values()));
    MemorySegment content =
        UserNotifications.content(
            notification.title(),
            notification.body(),
            identifier,
            image == null ? null : image.toString());
    Arena arena = Arena.ofAuto();
    long id = PENDING_REQUESTS.register(new PendingCompletion<>(added, arena));
    UserNotifications.add(
        this.center(), identifier, content, ObjC.block(arena, ON_REQUEST_ADDED, id));
    Foundation.release(content);
  }

  /** Asks the user once, and waits for the answer. */
  private synchronized void authorize() {
    if (this.authorized) {
      return;
    }
    CompletableFuture<Boolean> answer = new CompletableFuture<>();
    Arena arena = Arena.ofAuto();
    long id = PENDING_AUTHORIZATIONS.register(new PendingCompletion<>(answer, arena));
    this.dispatcher.run(
        () ->
            UserNotifications.requestAuthorization(
                this.center(), ObjC.block(arena, ON_AUTHORIZED, id)));
    try {
      this.authorized = answer.get(AUTHORIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the user", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new UnsupportedOperationException(
          "macOS didn't answer whether notifications may show", e);
    }
    if (!this.authorized) {
      throw new UnsupportedOperationException(
          "Notifications are off for "
              + UserNotifications.bundleIdentifier()
              + " in System Settings");
    }
  }

  /** Takes {@code notification} off the screen and forgets it. */
  void withdraw(MacNotification notification) {
    this.dispatcher.run(
        () -> {
          MemorySegment current = this.center;
          if (current != null) {
            UserNotifications.remove(current, notification.identifier());
          }
        });
    this.forget(notification.identifier());
  }

  /** Drops the table entry and the category of {@code identifier}. */
  void forget(String identifier) {
    this.shown.remove(identifier);
    MemorySegment category = this.categories.remove(identifier);
    if (category != null) {
      this.dispatcher.run(
          () -> {
            MemorySegment current = this.center;
            if (current != null) {
              UserNotifications.setCategories(current, List.copyOf(this.categories.values()));
            }
            Foundation.release(category);
          });
    }
  }

  /** Stops being the delegate. The notifications must be closed already. */
  void close() {
    this.dispatcher.run(
        () -> {
          MemorySegment current = this.center;
          final MemorySegment oldDelegate = this.delegate;
          this.center = null;
          this.delegate = null;
          if (current != null) {
            UserNotifications.setDelegate(current, MemorySegment.NULL);
          }
          if (oldDelegate != null) {
            DELEGATES.remove(oldDelegate.address());
            Foundation.release(oldDelegate);
          }
        });
    MacNotification.deleteQuietly(this.directory);
  }

  /** What the user did with notification {@code identifier}: {@code action} names the button. */
  void responded(String identifier, String action) {
    MacNotification notification = this.shown.get(identifier);
    if (notification == null) {
      return;
    }
    if (UserNotifications.DEFAULT_ACTION.equals(action)) {
      notification.activate();
    } else if (action.startsWith(BUTTON_ACTION_PREFIX)) {
      try {
        notification.pick(Integer.parseInt(action.substring(BUTTON_ACTION_PREFIX.length())));
      } catch (NumberFormatException _) {
        // Not one of ours: the button IDs are all numbered.
      }
    }
    // Every response ends the notification: macOS takes it away after a click or a dismissal.
    notification.markClosed();
  }

  private MemorySegment center() {
    MemorySegment current = this.center;
    if (current == null) {
      throw new IllegalStateException("The notifier is closed");
    }
    return current;
  }

  private synchronized Path writeImage(byte[] png) {
    try {
      if (this.directory == null) {
        this.directory = Files.createTempDirectory("lwjwae-notifications");
      }
      Path file = this.directory.resolve("image-" + IDS.incrementAndGet() + ".png");
      Files.write(file, png);
      return file;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static MemorySegment delegateStub(String method) {
    return NativeLibraries.upcall(
        MethodHandles.lookup(),
        MacNotifier.class,
        method,
        MethodType.methodType(
            void.class,
            MemorySegment.class,
            MemorySegment.class,
            MemorySegment.class,
            MemorySegment.class,
            MemorySegment.class),
        Signatures.DELEGATE_3);
  }

  // --- LwjwaeNotificationDelegate methods and completion blocks, bound by name above ---

  /**
   * Shows the notification even though the application is in front.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onWillPresent(
      MemorySegment self,
      MemorySegment command,
      MemorySegment center,
      MemorySegment notification,
      MemorySegment completion) {
    try {
      ObjC.callBlock(completion, UserNotifications.PRESENT_AS_BANNER);
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * The user clicked or dismissed a notification.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onDidReceiveResponse(
      MemorySegment self,
      MemorySegment command,
      MemorySegment center,
      MemorySegment response,
      MemorySegment completion) {
    try {
      MacNotifier notifier = DELEGATES.get(self.address());
      if (notifier != null) {
        notifier.responded(
            UserNotifications.responseNotification(response),
            UserNotifications.responseAction(response));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    } finally {
      ObjC.callBlock(completion);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onRequestAdded(MemorySegment block, MemorySegment error) {
    PendingCompletion<Optional<String>> pending =
        PENDING_REQUESTS.unregister(ObjC.blockContext(block));
    if (pending == null) {
      return;
    }
    try {
      pending
          .result()
          .complete(
              ObjC.isNull(error)
                  ? Optional.empty()
                  : Optional.of(Foundation.errorDescription(error)));
    } catch (Throwable t) {
      pending.result().completeExceptionally(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onAuthorized(MemorySegment block, boolean granted, MemorySegment error) {
    PendingCompletion<Boolean> pending =
        PENDING_AUTHORIZATIONS.unregister(ObjC.blockContext(block));
    if (pending != null) {
      pending.result().complete(granted);
    }
  }
}
