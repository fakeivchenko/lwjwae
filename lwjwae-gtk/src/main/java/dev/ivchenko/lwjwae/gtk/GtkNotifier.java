package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.AbstractNotification;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.gtk.binding.Dbus;
import dev.ivchenko.lwjwae.gtk.binding.Glib;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.TemporaryImages;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The notifications of one application, through the {@code org.freedesktop.Notifications} service
 * on the session bus.
 *
 * <p>Every Linux desktop with notifications implements that interface: GNOME Shell, Plasma, and the
 * standalone servers such as dunst and mako. It's called over GDBus, which is part of GIO and so
 * always present where GTK is; libnotify would be one more library to find, for no feature that the
 * interface itself lacks.
 *
 * <p>The server reports a click on a button with {@code ActionInvoked} and the end of a
 * notification with {@code NotificationClosed}, as signals. The notifier subscribes on the GTK
 * thread, so the signals arrive there, and it calls {@code Notify} on the GTK thread too: the ID
 * that the call returns is in the table before the loop can deliver any signal about it. The cost
 * is that the GTK thread waits for the reply, which a server sends at once.
 *
 * <p>The image goes to the server as a file, through the {@code image-path} hint, because the
 * alternative, raw pixels in {@code image-data}, would need the PNG decoded first. The file lives
 * in a temporary directory until its notification is gone, and the directory until the notifier is.
 */
final class GtkNotifier {
  private static final String SERVICE = "org.freedesktop.Notifications";
  private static final String OBJECT_PATH = "/org/freedesktop/Notifications";
  private static final int CALL_TIMEOUT_MILLIS = 5000;
  private static final int SERVER_DEFAULT_EXPIRY = -1;

  private static final CallbackRegistry<GtkNotifier> NOTIFIERS = new CallbackRegistry<>();

  private static final MemorySegment ON_SIGNAL =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkNotifier.class,
          "onSignal",
          MethodType.methodType(
              void.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class),
          Signatures.G_DBUS_SIGNAL_CALLBACK);

  private final UiDispatcher dispatcher;
  private final String applicationName;
  private final long callbackId;
  private final Map<Integer, GtkNotification> shown = new ConcurrentHashMap<>();

  private volatile MemorySegment connection;
  private volatile int subscription;
  private final TemporaryImages images = new TemporaryImages("lwjwae-notifications");

  /**
   * Connects to the session bus and subscribes to the signals of the server.
   *
   * @param applicationName What the desktop shows as the sender, or {@code null} for nothing.
   * @throws UnsupportedOperationException If there is no session bus.
   */
  GtkNotifier(UiDispatcher dispatcher, String applicationName) {
    this.dispatcher = dispatcher;
    this.applicationName = applicationName == null ? "" : applicationName;
    this.callbackId = NOTIFIERS.register(this);
    try {
      this.dispatcher.run(this::connect);
    } catch (IllegalStateException e) {
      NOTIFIERS.unregister(this.callbackId);
      throw new UnsupportedOperationException("No notifications: " + e.getMessage(), e);
    }
  }

  private void connect() {
    MemorySegment bus = Dbus.sessionBus();
    this.subscription =
        Dbus.subscribe(
            bus,
            SERVICE,
            SERVICE,
            OBJECT_PATH,
            ON_SIGNAL,
            CallbackRegistry.userData(this.callbackId));
    this.connection = bus;
  }

  /**
   * Hands {@code notification} to the server.
   *
   * @throws UnsupportedOperationException If no server answers on the bus.
   */
  GtkNotification show(Notification notification, Consumer<NotificationHandle> closed) {
    Path image = notification.icon() == null ? null : this.images.write(notification.icon());
    try {
      return this.dispatcher.call(
          () -> {
            int id = this.notify(notification, image);
            GtkNotification handle = new GtkNotification(this, id, notification, image, closed);
            this.shown.put(id, handle);
            return handle;
          });
    } catch (RuntimeException e) {
      TemporaryImages.delete(image);
      throw e;
    }
  }

  /** Calls {@code Notify}. Runs on the GTK thread. */
  private int notify(Notification notification, Path image) {
    List<MemorySegment> actions = new ArrayList<>();
    if (notification.onActivate() != null) {
      actions.add(Dbus.string(AbstractNotification.DEFAULT_ACTION));
      actions.add(Dbus.string(""));
    }
    List<NotificationAction> buttons = notification.actions();
    for (int index = 0; index < buttons.size(); index++) {
      actions.add(Dbus.string(AbstractNotification.buttonAction(index)));
      actions.add(Dbus.string(buttons.get(index).label()));
    }
    List<MemorySegment> hints = new ArrayList<>();
    if (image != null) {
      hints.add(Dbus.dictEntry("image-path", Dbus.string(image.toUri().toString())));
    }
    MemorySegment parameters =
        Dbus.tuple(
            List.of(
                Dbus.string(this.applicationName),
                Dbus.uint32(0),
                Dbus.string(""),
                Dbus.string(notification.title()),
                Dbus.string(notification.body() == null ? "" : notification.body()),
                Dbus.array("s", actions),
                Dbus.array("{sv}", hints),
                Dbus.int32(SERVER_DEFAULT_EXPIRY)));
    MemorySegment reply;
    try {
      reply =
          Dbus.call(
              this.connection(),
              SERVICE,
              OBJECT_PATH,
              SERVICE,
              "Notify",
              parameters,
              "(u)",
              CALL_TIMEOUT_MILLIS);
    } catch (IllegalStateException e) {
      throw new UnsupportedOperationException(
          "The desktop has no notification server: " + e.getMessage(), e);
    }
    try {
      return Dbus.uint32At(reply, 0);
    } finally {
      Dbus.unref(reply);
    }
  }

  /** Takes {@code notification} back: forgets it, and asks the server to close it. */
  void withdraw(GtkNotification notification) {
    if (this.shown.remove(notification.id(), notification)) {
      this.dispatcher.run(
          () -> {
            MemorySegment bus = this.connection;
            if (bus == null) {
              return;
            }
            try {
              MemorySegment reply =
                  Dbus.call(
                      bus,
                      SERVICE,
                      OBJECT_PATH,
                      SERVICE,
                      "CloseNotification",
                      Dbus.tuple(List.of(Dbus.uint32(notification.id()))),
                      null,
                      CALL_TIMEOUT_MILLIS);
              Dbus.unref(reply);
            } catch (IllegalStateException e) {
              // Already gone on the server side, or the server went away: nothing left to close.
              ThrowableUtil.report(e);
            }
          });
    }
  }

  /** Unsubscribes and disconnects. The notifications must be closed already. */
  void close() {
    NOTIFIERS.unregister(this.callbackId);
    this.dispatcher.run(
        () -> {
          MemorySegment bus = this.connection;
          this.connection = null;
          if (bus != null) {
            Dbus.unsubscribe(bus, this.subscription);
            Glib.unref(bus);
          }
        });
    this.images.deleteAll();
  }

  /** Runs what the server reported for the action {@code key} of notification {@code id}. */
  void actionInvoked(int id, String key) {
    GtkNotification notification = this.shown.get(id);
    if (notification != null) {
      notification.invoked(key);
    }
  }

  /** Forgets notification {@code id}, which the server reported gone. */
  void notificationClosed(int id) {
    GtkNotification notification = this.shown.remove(id);
    if (notification != null) {
      notification.closedByServer();
    }
  }

  private MemorySegment connection() {
    MemorySegment bus = this.connection;
    if (bus == null) {
      throw new IllegalStateException("The notifier is closed");
    }
    return bus;
  }

  // --- the signal callback, bound by name from the upcall stub above ---

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onSignal(
      MemorySegment connection,
      MemorySegment sender,
      MemorySegment objectPath,
      MemorySegment interfaceName,
      MemorySegment signalName,
      MemorySegment parameters,
      MemorySegment userData) {
    try {
      GtkNotifier notifier = NOTIFIERS.lookup(userData);
      if (notifier == null) {
        return;
      }
      String signal = NativeLibraries.string(signalName);
      if ("ActionInvoked".equals(signal)) {
        notifier.actionInvoked(Dbus.uint32At(parameters, 0), Dbus.stringAt(parameters, 1));
      } else if ("NotificationClosed".equals(signal)) {
        notifier.notificationClosed(Dbus.uint32At(parameters, 0));
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
