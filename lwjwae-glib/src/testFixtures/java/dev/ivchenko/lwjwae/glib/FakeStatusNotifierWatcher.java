package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Dbus;
import dev.ivchenko.lwjwae.glib.binding.Signatures;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A StatusNotifierWatcher for a session bus that has none, such as the one that CI starts for the
 * display tests: it accepts every item and lists them, and hosts nothing.
 *
 * <p>On a desktop with a panel, the real watcher owns the name already, and this one never starts,
 * so the tests talk to the panel. Without it, {@link StatusNotifierTray} would find no watcher and
 * refuse, and the tray contract couldn't run where no panel is.
 */
public final class FakeStatusNotifierWatcher {
  private static final String NAME = "org.kde.StatusNotifierWatcher";
  private static final String PATH = "/StatusNotifierWatcher";
  private static final String XML =
      """
      <node>
        <interface name="org.kde.StatusNotifierWatcher">
          <property name="RegisteredStatusNotifierItems" type="as" access="read"/>
          <property name="IsStatusNotifierHostRegistered" type="b" access="read"/>
          <property name="ProtocolVersion" type="i" access="read"/>
          <method name="RegisterStatusNotifierItem"><arg name="service" type="s" direction="in"/></method>
          <method name="RegisterStatusNotifierHost"><arg name="service" type="s" direction="in"/></method>
        </interface>
      </node>
      """;

  private static final List<String> ITEMS = new CopyOnWriteArrayList<>();

  private static final MemorySegment ON_METHOD_CALL =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          FakeStatusNotifierWatcher.class,
          "onMethodCall",
          MethodType.methodType(
              void.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class),
          Signatures.G_DBUS_METHOD_CALL);
  private static final MemorySegment ON_GET_PROPERTY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          FakeStatusNotifierWatcher.class,
          "onGetProperty",
          MethodType.methodType(
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class),
          Signatures.G_DBUS_GET_PROPERTY);

  private static boolean started;

  private FakeStatusNotifierWatcher() {}

  /**
   * Starts the watcher unless the session has one. Its calls arrive on the thread of {@code
   * dispatcher}, which must not be the one that registers the items.
   */
  public static synchronized void ensureRunning(UiDispatcher dispatcher) {
    if (started) {
      return;
    }
    dispatcher.run(
        () -> {
          MemorySegment bus = Dbus.privateSessionBus();
          if (FakeStatusNotifierWatcher.hasOwner(bus)) {
            Dbus.close(bus);
            return;
          }
          Dbus.registerObject(
              bus,
              PATH,
              Dbus.interfaceInfo(XML, NAME),
              Dbus.vtable(ON_METHOD_CALL, ON_GET_PROPERTY),
              MemorySegment.NULL);
          MemorySegment reply =
              Dbus.call(
                  bus,
                  "org.freedesktop.DBus",
                  "/org/freedesktop/DBus",
                  "org.freedesktop.DBus",
                  "RequestName",
                  Dbus.tuple(List.of(Dbus.string(NAME), Dbus.uint32(0))),
                  "(u)",
                  5000);
          Dbus.unref(reply);
          // The connection stays open for the rest of the test JVM: that is the watcher.
        });
    started = true;
  }

  private static boolean hasOwner(MemorySegment bus) {
    try {
      MemorySegment reply =
          Dbus.call(
              bus,
              "org.freedesktop.DBus",
              "/org/freedesktop/DBus",
              "org.freedesktop.DBus",
              "GetNameOwner",
              Dbus.tuple(List.of(Dbus.string(NAME))),
              "(s)",
              5000);
      Dbus.unref(reply);
      return true;
    } catch (IllegalStateException _) {
      return false;
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onMethodCall(
      MemorySegment connection,
      MemorySegment sender,
      MemorySegment objectPath,
      MemorySegment interfaceName,
      MemorySegment methodName,
      MemorySegment parameters,
      MemorySegment invocation,
      MemorySegment userData) {
    try {
      if ("RegisterStatusNotifierItem".equals(NativeLibraries.string(methodName))) {
        ITEMS.add(Dbus.stringAt(parameters, 0));
      }
      Dbus.returnValue(invocation, Dbus.tuple(List.of()));
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static MemorySegment onGetProperty(
      MemorySegment connection,
      MemorySegment sender,
      MemorySegment objectPath,
      MemorySegment interfaceName,
      MemorySegment propertyName,
      MemorySegment error,
      MemorySegment userData) {
    try {
      return switch (NativeLibraries.string(propertyName)) {
        case "RegisteredStatusNotifierItems" ->
            Dbus.array("s", ITEMS.stream().map(Dbus::string).toList());
        case "IsStatusNotifierHostRegistered" -> Dbus.bool(true);
        case "ProtocolVersion" -> Dbus.int32(0);
        default -> MemorySegment.NULL;
      };
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      return MemorySegment.NULL;
    }
  }
}
