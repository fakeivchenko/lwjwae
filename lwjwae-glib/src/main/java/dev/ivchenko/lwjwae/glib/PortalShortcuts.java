package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.exception.ShortcutUnavailableException;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Dbus;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.Signatures;
import dev.ivchenko.lwjwae.glib.util.KeysymUtil;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.shortcut.ShortcutModifier;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Global shortcuts on Wayland, through the {@code GlobalShortcuts} portal of the desktop, where a
 * client can't grab keys itself.
 *
 * <p>Each shortcut gets a session of its own: {@code CreateSession}, then {@code BindShortcuts} of
 * that one shortcut, which the desktop may show to the user to confirm, and {@code Session.Close}
 * to give it back. One session per shortcut keeps giving one back from touching the others, since a
 * session binds its shortcuts once. The portal presses them with the {@code Activated} signal.
 *
 * <p>Every method of the portal answers with a request object, whose {@code Response} signal
 * carries the result later. The path of that object follows from the {@code handle_token} of the
 * call, so the subscription to it is in place before the call, and so is the path of the session.
 * Everything runs on the GTK thread, where the signals arrive.
 */
public final class PortalShortcuts implements DesktopShortcuts {
  private static final String SERVICE = "org.freedesktop.portal.Desktop";
  private static final String OBJECT_PATH = "/org/freedesktop/portal/desktop";
  private static final String INTERFACE = "org.freedesktop.portal.GlobalShortcuts";
  private static final String REQUEST = "org.freedesktop.portal.Request";
  private static final String SESSION = "org.freedesktop.portal.Session";
  private static final int CALL_TIMEOUT_MILLIS = 5000;

  /** How long the token of a press lets a window come to the front: the handler runs by then. */
  private static final long ACTIVATION_NANOS = 10_000_000_000L;

  private static final CallbackRegistry<PortalShortcuts> PORTALS = new CallbackRegistry<>();

  /** The {@code activation_token} of the last press, and when it came, or {@code null}. */
  private static volatile String activationToken;

  private static volatile long activationTime;

  private static final MemorySegment ON_SIGNAL =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          PortalShortcuts.class,
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
  private final String description;
  private final long callbackId;
  private final AtomicLong tokens = new AtomicLong();
  private final Map<String, Runnable> sessions = new ConcurrentHashMap<>();
  private final Map<String, IntConsumer> requests = new ConcurrentHashMap<>();
  private final Map<String, Integer> requestSubscriptions = new ConcurrentHashMap<>();

  private volatile MemorySegment connection;
  private volatile String sender;
  private volatile int activated;

  /**
   * Connects to the session bus and checks for the portal.
   *
   * @param description What the desktop shows for the shortcuts of this application, next to each.
   * @throws UnsupportedOperationException If there is no session bus, or it has no {@code
   *     GlobalShortcuts} portal.
   */
  public PortalShortcuts(UiDispatcher dispatcher, String description) {
    this.dispatcher = dispatcher;
    this.description = description;
    this.callbackId = PORTALS.register(this);
    try {
      this.dispatcher.run(this::connect);
    } catch (IllegalStateException e) {
      PORTALS.unregister(this.callbackId);
      throw new UnsupportedOperationException(
          "The desktop has no GlobalShortcuts portal: " + e.getMessage(), e);
    }
  }

  private void connect() {
    MemorySegment bus = Dbus.sessionBus();
    try {
      MemorySegment reply =
          Dbus.call(
              bus,
              SERVICE,
              OBJECT_PATH,
              "org.freedesktop.DBus.Properties",
              "Get",
              Dbus.tuple(List.of(Dbus.string(INTERFACE), Dbus.string("version"))),
              "(v)",
              CALL_TIMEOUT_MILLIS);
      Dbus.unref(reply);
    } catch (IllegalStateException e) {
      Glib.unref(bus);
      throw e;
    }
    // The path of a request or a session holds the unique name of the caller, as the portal writes
    // it: without the colon, dots as underscores.
    this.sender = Dbus.uniqueName(bus).substring(1).replace('.', '_');
    this.activated =
        Dbus.subscribe(
            bus,
            SERVICE,
            INTERFACE,
            OBJECT_PATH,
            ON_SIGNAL,
            CallbackRegistry.userData(this.callbackId));
    this.connection = bus;
  }

  /**
   * Asks the portal for {@code shortcut}; {@code pressed} runs on the GTK thread on every press.
   * Returns before the desktop confirms: a shortcut that the user turns down is reported to {@link
   * ThrowableUtil#report} and never runs.
   */
  @Override
  public EventSubscription bind(Shortcut shortcut, Runnable pressed) {
    String sessionToken = this.token();
    String session = OBJECT_PATH + "/session/" + this.sender + "/" + sessionToken;
    this.sessions.put(session, pressed);
    this.dispatcher.run(
        () ->
            this.request(
                "CreateSession",
                handle ->
                    Dbus.tuple(
                        List.of(
                            Dbus.array(
                                "{sv}",
                                List.of(
                                    Dbus.dictEntry("handle_token", Dbus.string(handle)),
                                    Dbus.dictEntry(
                                        "session_handle_token", Dbus.string(sessionToken)))))),
                response -> {
                  if (response != 0) {
                    this.refused(session, shortcut, "the portal made no session, " + response);
                  } else if (this.sessions.containsKey(session)) {
                    this.bindShortcut(session, shortcut);
                  } else {
                    this.closeSession(session);
                  }
                }));
    return () -> {
      if (this.sessions.remove(session) != null) {
        this.dispatcher.run(() -> this.closeSession(session));
      }
    };
  }

  private void bindShortcut(String session, Shortcut shortcut) {
    // The desktop keeps the shortcuts of every client that it counts as one application under one
    // name, often the application that started this process, so the ID says whose it is.
    String name = this.description + ": " + shortcut;
    MemorySegment entry =
        Dbus.tuple(
            List.of(
                Dbus.string(name),
                Dbus.array(
                    "{sv}",
                    List.of(
                        Dbus.dictEntry("description", Dbus.string(name)),
                        Dbus.dictEntry(
                            "preferred_trigger",
                            Dbus.string(PortalShortcuts.trigger(shortcut)))))));
    this.request(
        "BindShortcuts",
        handle ->
            Dbus.tuple(
                List.of(
                    Dbus.objectPath(session),
                    Dbus.array("(sa{sv})", List.of(entry)),
                    Dbus.string(""),
                    Dbus.array(
                        "{sv}", List.of(Dbus.dictEntry("handle_token", Dbus.string(handle)))))),
        response -> {
          if (response != 0) {
            this.refused(session, shortcut, "the user or the desktop turned it down, " + response);
          }
        });
  }

  /**
   * Calls {@code method} of the portal with the parameters that {@code parameters} makes of a new
   * handle token, and hands the code of its {@code Response} to {@code response}: 0 for success, 1
   * when the user canceled, 2 otherwise.
   */
  private void request(
      String method, Function<String, MemorySegment> parameters, IntConsumer response) {
    String handle = this.token();
    String path = OBJECT_PATH + "/request/" + this.sender + "/" + handle;
    MemorySegment bus = this.connection();
    this.requests.put(path, response);
    this.requestSubscriptions.put(
        path,
        Dbus.subscribe(
            bus, SERVICE, REQUEST, path, ON_SIGNAL, CallbackRegistry.userData(this.callbackId)));
    try {
      MemorySegment reply =
          Dbus.call(
              bus,
              SERVICE,
              OBJECT_PATH,
              INTERFACE,
              method,
              parameters.apply(handle),
              "(o)",
              CALL_TIMEOUT_MILLIS);
      Dbus.unref(reply);
    } catch (IllegalStateException e) {
      this.forgetRequest(path);
      throw new ShortcutUnavailableException(
          "The portal refused " + method + ": " + e.getMessage());
    }
  }

  private void refused(String session, Shortcut shortcut, String why) {
    if (this.sessions.remove(session) != null) {
      this.closeSession(session);
    }
    ThrowableUtil.report(new ShortcutUnavailableException(shortcut + " is not bound: " + why));
  }

  private void closeSession(String session) {
    MemorySegment bus = this.connection;
    if (bus == null) {
      return;
    }
    try {
      Dbus.unref(
          Dbus.call(
              bus,
              SERVICE,
              session,
              SESSION,
              "Close",
              Dbus.tuple(List.of()),
              null,
              CALL_TIMEOUT_MILLIS));
    } catch (IllegalStateException e) {
      // The session never came to be, or the portal closed it already.
      ThrowableUtil.report(e);
    }
  }

  private IntConsumer forgetRequest(String path) {
    Integer subscription = this.requestSubscriptions.remove(path);
    MemorySegment bus = this.connection;
    if (subscription != null && bus != null) {
      Dbus.unsubscribe(bus, subscription);
    }
    return this.requests.remove(path);
  }

  /** Gives every shortcut back and disconnects. */
  @Override
  public void close() {
    PORTALS.unregister(this.callbackId);
    this.dispatcher.run(
        () -> {
          for (String session : new ArrayList<>(this.sessions.keySet())) {
            this.sessions.remove(session);
            this.closeSession(session);
          }
          for (String path : new ArrayList<>(this.requests.keySet())) {
            this.forgetRequest(path);
          }
          MemorySegment bus = this.connection;
          this.connection = null;
          if (bus != null) {
            Dbus.unsubscribe(bus, this.activated);
            Glib.unref(bus);
          }
        });
  }

  private String token() {
    return "lwjwae" + this.tokens.incrementAndGet();
  }

  private MemorySegment connection() {
    MemorySegment bus = this.connection;
    if (bus == null) {
      throw new IllegalStateException("The shortcuts are closed");
    }
    return bus;
  }

  /**
   * Takes the activation token of the last press of a shortcut, if one came in the last seconds,
   * which lets a window of the application come to the front: Wayland lets a client take the focus
   * only with a token that the compositor handed out for an input of the user. The press itself
   * went to the portal, so the portal hands the token on, from version 2. Call on the GTK thread,
   * right before the window is presented.
   *
   * @return The token, once, or {@code null}.
   */
  public static String takeActivationToken() {
    String token = PortalShortcuts.activationToken;
    PortalShortcuts.activationToken = null;
    return token != null && System.nanoTime() - PortalShortcuts.activationTime < ACTIVATION_NANOS
        ? token
        : null;
  }

  /**
   * The trigger that the portal proposes to the user, in the form of the shortcuts specification of
   * freedesktop: {@code CTRL+ALT+SHIFT+LOGO+} and the keysym name.
   */
  static String trigger(Shortcut shortcut) {
    StringBuilder trigger = new StringBuilder();
    if (shortcut.has(ShortcutModifier.CONTROL)) {
      trigger.append("CTRL+");
    }
    if (shortcut.has(ShortcutModifier.ALT)) {
      trigger.append("ALT+");
    }
    if (shortcut.has(ShortcutModifier.SHIFT)) {
      trigger.append("SHIFT+");
    }
    if (shortcut.has(ShortcutModifier.META)) {
      trigger.append("LOGO+");
    }
    return trigger.append(KeysymUtil.name(shortcut.key())).toString();
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
      PortalShortcuts portal = PORTALS.lookup(userData);
      if (portal == null) {
        return;
      }
      String signal = NativeLibraries.string(signalName);
      if ("Response".equals(signal)) {
        IntConsumer response = portal.forgetRequest(NativeLibraries.string(objectPath));
        if (response != null) {
          response.accept(Dbus.uint32At(parameters, 0));
        }
      } else if ("Activated".equals(signal)) {
        Runnable pressed = portal.sessions.get(Dbus.stringAt(parameters, 0));
        if (pressed != null) {
          MemorySegment options = Dbus.child(parameters, 3);
          try {
            String token = Dbus.lookupString(options, "activation_token");
            if (token != null) {
              PortalShortcuts.activationTime = System.nanoTime();
              PortalShortcuts.activationToken = token;
            }
          } finally {
            Dbus.unref(options);
          }
          pressed.run();
        }
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
