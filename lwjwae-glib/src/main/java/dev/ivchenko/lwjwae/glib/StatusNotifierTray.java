package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Dbus;
import dev.ivchenko.lwjwae.glib.binding.GdkPixbuf;
import dev.ivchenko.lwjwae.glib.binding.Pixmap;
import dev.ivchenko.lwjwae.glib.binding.Signatures;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import dev.ivchenko.lwjwae.tray.TrayMenuItem;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.HandlerUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A tray icon on Linux: a StatusNotifierItem with a dbusmenu, served by the backend itself over
 * D-Bus, with no toolkit involved.
 *
 * <p>GTK 4 has no tray of its own: {@code GtkStatusIcon} is gone, and libappindicator links GTK 3,
 * which can't share a process with GTK 4. Under GTK 3, libappindicator may be missing, and {@code
 * GtkStatusIcon} needs X11. What a panel talks to, though, is only D-Bus: KDE, GNOME with its
 * AppIndicator extension, and most other panels read an object with the {@code
 * org.kde.StatusNotifierItem} interface for the icon and one with {@code com.canonical.dbusmenu}
 * for the menu. The tray exports both through GDBus, which ships with GLib, and registers the item
 * with the {@code org.kde.StatusNotifierWatcher} of the session.
 *
 * <p>Each tray has a connection of its own to the session bus, so a unique name of its own. The
 * watcher tracks an item by that name, and closing the connection is what takes the icon off the
 * panel; a shared connection would keep every icon up until the process exits. The objects are
 * registered on the GTK thread, so their method calls and property reads arrive there; the call to
 * the watcher is made from the calling thread, because the watcher reads those properties before it
 * answers, and a GTK thread blocked in the call couldn't serve them.
 *
 * <p>The image goes to the panel as pixels, in {@code IconPixmap}, with an empty {@code IconName}.
 * A name would be looked up in the icon theme first, and a theme that has no icon of that name
 * falls back by cutting the name at its dashes: KDE drew {@code image-1} as the generic {@code
 * image} icon. {@link GdkPixbuf#argb32} decodes the PNG into the ARGB32 in network byte order that
 * the specification asks for.
 *
 * <p>A primary click reaches {@code Activate}, which runs {@link TrayIcon#onActivate()}. Without
 * that handler, the item says it is only a menu ({@code ItemIsMenu}), and the panel opens the menu
 * on any click. Menu entries have the IDs 1 and up in their order; the root of the layout is 0.
 */
public class StatusNotifierTray implements Tray {
  private static final String ITEM_INTERFACE = "org.kde.StatusNotifierItem";
  private static final String MENU_INTERFACE = "com.canonical.dbusmenu";
  private static final String ITEM_PATH = "/StatusNotifierItem";
  private static final String MENU_PATH = "/MenuBar";
  private static final String WATCHER = "org.kde.StatusNotifierWatcher";
  private static final String WATCHER_PATH = "/StatusNotifierWatcher";
  private static final int CALL_TIMEOUT_MILLIS = 5000;
  private static final int DBUSMENU_VERSION = 3;

  private static final String ITEM_XML =
      """
      <node>
        <interface name="org.kde.StatusNotifierItem">
          <property name="Category" type="s" access="read"/>
          <property name="Id" type="s" access="read"/>
          <property name="Title" type="s" access="read"/>
          <property name="Status" type="s" access="read"/>
          <property name="WindowId" type="i" access="read"/>
          <property name="IconName" type="s" access="read"/>
          <property name="IconThemePath" type="s" access="read"/>
          <property name="IconPixmap" type="a(iiay)" access="read"/>
          <property name="OverlayIconName" type="s" access="read"/>
          <property name="OverlayIconPixmap" type="a(iiay)" access="read"/>
          <property name="AttentionIconName" type="s" access="read"/>
          <property name="AttentionIconPixmap" type="a(iiay)" access="read"/>
          <property name="AttentionMovieName" type="s" access="read"/>
          <property name="ToolTip" type="(sa(iiay)ss)" access="read"/>
          <property name="ItemIsMenu" type="b" access="read"/>
          <property name="Menu" type="o" access="read"/>
          <method name="ContextMenu"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/></method>
          <method name="Activate"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/></method>
          <method name="SecondaryActivate"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/></method>
          <method name="Scroll"><arg name="delta" type="i" direction="in"/><arg name="orientation" type="s" direction="in"/></method>
          <signal name="NewTitle"/>
          <signal name="NewIcon"/>
          <signal name="NewAttentionIcon"/>
          <signal name="NewOverlayIcon"/>
          <signal name="NewToolTip"/>
          <signal name="NewStatus"><arg name="status" type="s"/></signal>
        </interface>
      </node>
      """;

  private static final String MENU_XML =
      """
      <node>
        <interface name="com.canonical.dbusmenu">
          <property name="Version" type="u" access="read"/>
          <property name="TextDirection" type="s" access="read"/>
          <property name="Status" type="s" access="read"/>
          <property name="IconThemePath" type="as" access="read"/>
          <method name="GetLayout">
            <arg name="parentId" type="i" direction="in"/>
            <arg name="recursionDepth" type="i" direction="in"/>
            <arg name="propertyNames" type="as" direction="in"/>
            <arg name="revision" type="u" direction="out"/>
            <arg name="layout" type="(ia{sv}av)" direction="out"/>
          </method>
          <method name="GetGroupProperties">
            <arg name="ids" type="ai" direction="in"/>
            <arg name="propertyNames" type="as" direction="in"/>
            <arg name="properties" type="a(ia{sv})" direction="out"/>
          </method>
          <method name="GetProperty">
            <arg name="id" type="i" direction="in"/>
            <arg name="name" type="s" direction="in"/>
            <arg name="value" type="v" direction="out"/>
          </method>
          <method name="Event">
            <arg name="id" type="i" direction="in"/>
            <arg name="eventId" type="s" direction="in"/>
            <arg name="data" type="v" direction="in"/>
            <arg name="timestamp" type="u" direction="in"/>
          </method>
          <method name="EventGroup">
            <arg name="events" type="a(isvu)" direction="in"/>
            <arg name="idErrors" type="ai" direction="out"/>
          </method>
          <method name="AboutToShow">
            <arg name="id" type="i" direction="in"/>
            <arg name="needUpdate" type="b" direction="out"/>
          </method>
          <method name="AboutToShowGroup">
            <arg name="ids" type="ai" direction="in"/>
            <arg name="updatesNeeded" type="ai" direction="out"/>
            <arg name="idErrors" type="ai" direction="out"/>
          </method>
          <signal name="ItemsPropertiesUpdated">
            <arg name="updatedProps" type="a(ia{sv})"/>
            <arg name="removedProps" type="a(ias)"/>
          </signal>
          <signal name="LayoutUpdated">
            <arg name="revision" type="u"/>
            <arg name="parent" type="i"/>
          </signal>
          <signal name="ItemActivationRequested">
            <arg name="id" type="i"/>
            <arg name="timestamp" type="u"/>
          </signal>
        </interface>
      </node>
      """;

  private static final CallbackRegistry<StatusNotifierTray> TRAYS = new CallbackRegistry<>();
  private static final AtomicInteger IDS = new AtomicInteger();

  private static final MemorySegment ON_METHOD_CALL =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          StatusNotifierTray.class,
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
          StatusNotifierTray.class,
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

  private static MemorySegment itemInterface;
  private static MemorySegment menuInterface;
  private static MemorySegment vtable;

  private final UiDispatcher dispatcher;
  private final Consumer<Tray> closedCallback;
  private final long callbackId;
  private final String name;
  private final Runnable onActivate;
  private final AtomicInteger revision = new AtomicInteger(1);

  private volatile MemorySegment connection;
  private volatile int itemRegistration;
  private volatile int menuRegistration;
  private volatile Pixmap pixmap;
  private volatile String tooltip;
  private volatile List<TrayMenuItem> menu;
  private volatile boolean closed;

  /**
   * Puts the icon up and returns when the watcher has it.
   *
   * @param closed Told once, when the icon goes away, so that the application stops counting it.
   * @throws UnsupportedOperationException If the session has no bus or no StatusNotifier host.
   * @throws IllegalArgumentException If gdk-pixbuf can't read the image.
   */
  public StatusNotifierTray(UiDispatcher dispatcher, TrayIcon icon, Consumer<Tray> closed) {
    this.dispatcher = dispatcher;
    this.closedCallback = closed;
    this.name = "lwjwae-tray-" + ProcessHandle.current().pid() + "-" + IDS.incrementAndGet();
    this.onActivate = icon.onActivate();
    this.tooltip = icon.tooltip();
    this.menu = icon.menu();
    this.pixmap = GdkPixbuf.argb32(icon.icon());
    this.callbackId = TRAYS.register(this);
    try {
      this.dispatcher.run(this::export);
    } catch (RuntimeException | Error e) {
      TRAYS.unregister(this.callbackId);
      throw e;
    }
    this.register();
  }

  /**
   * Opens the connection and exports the two objects. Runs on the GTK thread, once, so that their
   * method calls and property reads arrive there.
   */
  private void export() {
    prepareInterfaces();
    MemorySegment bus;
    try {
      bus = Dbus.privateSessionBus();
    } catch (IllegalStateException e) {
      throw new UnsupportedOperationException("No tray: " + e.getMessage(), e);
    }
    MemorySegment userData = CallbackRegistry.userData(this.callbackId);
    try {
      this.itemRegistration = Dbus.registerObject(bus, ITEM_PATH, itemInterface, vtable, userData);
      this.menuRegistration = Dbus.registerObject(bus, MENU_PATH, menuInterface, vtable, userData);
    } catch (IllegalStateException e) {
      Dbus.close(bus);
      throw e;
    }
    this.connection = bus;
  }

  /**
   * Hands the item to the watcher. Runs on the calling thread, not the GTK thread: the call waits
   * for the watcher, which reads the properties of the item before it answers, and those reads are
   * served on the GTK thread.
   *
   * @throws UnsupportedOperationException If the session has no watcher.
   */
  private void register() {
    MemorySegment bus = this.connection;
    try {
      MemorySegment reply =
          Dbus.call(
              bus,
              WATCHER,
              WATCHER_PATH,
              WATCHER,
              "RegisterStatusNotifierItem",
              Dbus.tuple(List.of(Dbus.string(Dbus.uniqueName(bus)))),
              null,
              CALL_TIMEOUT_MILLIS);
      Dbus.unref(reply);
    } catch (IllegalStateException e) {
      this.closed = true;
      TRAYS.unregister(this.callbackId);
      this.dispatcher.run(this::disconnect);
      throw new UnsupportedOperationException(
          "No tray: the desktop has no StatusNotifier host (" + e.getMessage() + ")", e);
    }
  }

  /** Takes the objects off the bus and closes the connection. Runs on the GTK thread. */
  private void disconnect() {
    MemorySegment bus = this.connection;
    this.connection = null;
    if (bus != null) {
      Dbus.unregisterObject(bus, this.itemRegistration);
      Dbus.unregisterObject(bus, this.menuRegistration);
      // The watcher sees the unique name go, and the panel drops the icon.
      Dbus.close(bus);
    }
  }

  private static synchronized void prepareInterfaces() {
    if (vtable == null) {
      itemInterface = Dbus.interfaceInfo(ITEM_XML, ITEM_INTERFACE);
      menuInterface = Dbus.interfaceInfo(MENU_XML, MENU_INTERFACE);
      vtable = Dbus.vtable(ON_METHOD_CALL, ON_GET_PROPERTY);
    }
  }

  @Override
  public void icon(byte[] png) {
    this.checkOpen();
    this.pixmap = GdkPixbuf.argb32(png);
    this.emit(ITEM_PATH, ITEM_INTERFACE, "NewIcon", StatusNotifierTray::noArguments);
  }

  @Override
  public void tooltip(String tooltip) {
    this.checkOpen();
    this.tooltip = tooltip;
    this.emit(ITEM_PATH, ITEM_INTERFACE, "NewToolTip", StatusNotifierTray::noArguments);
    this.emit(ITEM_PATH, ITEM_INTERFACE, "NewTitle", StatusNotifierTray::noArguments);
  }

  @Override
  public void menu(List<TrayMenuItem> items) {
    this.checkOpen();
    this.menu = List.copyOf(items);
    int next = this.revision.incrementAndGet();
    this.emit(
        MENU_PATH,
        MENU_INTERFACE,
        "LayoutUpdated",
        () -> Dbus.tuple(List.of(Dbus.uint32(next), Dbus.int32(0))));
  }

  @Override
  public boolean isClosed() {
    return this.closed;
  }

  @Override
  public void close() {
    if (this.closed) {
      return;
    }
    this.closed = true;
    TRAYS.unregister(this.callbackId);
    this.dispatcher.run(this::disconnect);
    this.closedCallback.accept(this);
  }

  /** Clicks menu entry {@code index}, as the panel does. For tests: no test can click a panel. */
  public void simulateMenuClick(int index) {
    this.dispatcher.run(() -> this.menuEvent(index + 1, "clicked"));
  }

  /** Clicks the icon with the primary button, as the panel does. For tests. */
  public void simulateActivate() {
    this.dispatcher.run(() -> HandlerUtil.runOffTheUiThread(this.onActivate));
  }

  private void checkOpen() {
    if (this.closed) {
      throw new IllegalStateException("The tray icon is closed");
    }
  }

  /** Emits a signal on the GTK thread; {@code parameters} builds the floating tuple there. */
  private void emit(
      String path, String interfaceName, String signal, Supplier<MemorySegment> parameters) {
    this.dispatcher.run(
        () -> {
          MemorySegment bus = this.connection;
          if (bus != null) {
            Dbus.emitSignal(bus, path, interfaceName, signal, parameters.get());
          }
        });
  }

  private static MemorySegment noArguments() {
    return Dbus.tuple(List.of());
  }

  // --- the StatusNotifierItem ---

  private MemorySegment itemProperty(String property) {
    String text = this.tooltip == null ? "" : this.tooltip;
    return switch (property) {
      case "Category" -> Dbus.string("ApplicationStatus");
      case "Id" -> Dbus.string(this.name);
      case "Title" -> Dbus.string(text);
      case "Status" -> Dbus.string("Active");
      case "WindowId" -> Dbus.int32(0);
      case "IconPixmap" -> pixmaps(this.pixmap);
      case "OverlayIconPixmap", "AttentionIconPixmap" -> noPixmaps();
      case "IconName",
          "IconThemePath",
          "OverlayIconName",
          "AttentionIconName",
          "AttentionMovieName" ->
          Dbus.string("");
      case "ToolTip" ->
          Dbus.tuple(List.of(Dbus.string(""), noPixmaps(), Dbus.string(text), Dbus.string("")));
      case "ItemIsMenu" -> Dbus.bool(this.onActivate == null);
      case "Menu" -> Dbus.objectPath(MENU_PATH);
      default -> null;
    };
  }

  private void itemCall(String method, MemorySegment invocation) {
    if ("Activate".equals(method)) {
      HandlerUtil.runOffTheUiThread(this.onActivate);
    }
    Dbus.returnValue(invocation, Dbus.tuple(List.of()));
  }

  // --- the dbusmenu ---

  private MemorySegment menuProperty(String property) {
    return switch (property) {
      case "Version" -> Dbus.uint32(DBUSMENU_VERSION);
      case "TextDirection" -> Dbus.string("ltr");
      case "Status" -> Dbus.string("normal");
      case "IconThemePath" -> Dbus.array("s", List.of());
      default -> null;
    };
  }

  private void menuCall(String method, MemorySegment parameters, MemorySegment invocation) {
    switch (method) {
      case "GetLayout" ->
          Dbus.returnValue(
              invocation, Dbus.tuple(List.of(Dbus.uint32(this.revision.get()), this.layout())));
      case "GetGroupProperties" -> {
        MemorySegment ids = Dbus.child(parameters, 0);
        List<MemorySegment> entries = new ArrayList<>();
        try {
          for (int index = 0; index < Dbus.childCount(ids); index++) {
            int id = Dbus.int32At(ids, index);
            entries.add(Dbus.tuple(List.of(Dbus.int32(id), this.entryProperties(id))));
          }
        } finally {
          Dbus.unref(ids);
        }
        Dbus.returnValue(invocation, Dbus.tuple(List.of(Dbus.array("(ia{sv})", entries))));
      }
      case "GetProperty" -> {
        int id = Dbus.int32At(parameters, 0);
        String key = Dbus.stringAt(parameters, 1);
        MemorySegment value = this.entryProperty(id, key);
        Dbus.returnValue(
            invocation, Dbus.tuple(List.of(Dbus.variant(value == null ? Dbus.string("") : value))));
      }
      case "Event" -> {
        this.menuEvent(Dbus.int32At(parameters, 0), Dbus.stringAt(parameters, 1));
        Dbus.returnValue(invocation, Dbus.tuple(List.of()));
      }
      case "EventGroup" -> {
        MemorySegment events = Dbus.child(parameters, 0);
        try {
          for (int index = 0; index < Dbus.childCount(events); index++) {
            MemorySegment event = Dbus.child(events, index);
            try {
              this.menuEvent(Dbus.int32At(event, 0), Dbus.stringAt(event, 1));
            } finally {
              Dbus.unref(event);
            }
          }
        } finally {
          Dbus.unref(events);
        }
        Dbus.returnValue(invocation, Dbus.tuple(List.of(Dbus.array("i", List.of()))));
      }
      case "AboutToShow" -> Dbus.returnValue(invocation, Dbus.tuple(List.of(Dbus.bool(false))));
      case "AboutToShowGroup" ->
          Dbus.returnValue(
              invocation,
              Dbus.tuple(List.of(Dbus.array("i", List.of()), Dbus.array("i", List.of()))));
      default -> Dbus.returnValue(invocation, Dbus.tuple(List.of()));
    }
  }

  /** The whole menu, as {@code (ia{sv}av)}: the root, 0, with one child per entry. */
  private MemorySegment layout() {
    List<TrayMenuItem> items = this.menu;
    List<MemorySegment> children = new ArrayList<>();
    for (int index = 0; index < items.size(); index++) {
      int id = index + 1;
      children.add(
          Dbus.variant(
              Dbus.tuple(
                  List.of(Dbus.int32(id), this.entryProperties(id), Dbus.array("v", List.of())))));
    }
    return Dbus.tuple(
        List.of(
            Dbus.int32(0),
            Dbus.array("{sv}", List.of(Dbus.dictEntry("children-display", Dbus.string("submenu")))),
            Dbus.array("v", children)));
  }

  /** The properties of entry {@code id} as {@code a{sv}}; unknown IDs have none. */
  private MemorySegment entryProperties(int id) {
    TrayMenuItem item = this.entry(id);
    List<MemorySegment> properties = new ArrayList<>();
    if (item != null && item.isSeparator()) {
      properties.add(Dbus.dictEntry("type", Dbus.string("separator")));
    } else if (item != null) {
      properties.add(Dbus.dictEntry("label", Dbus.string(item.label())));
      properties.add(Dbus.dictEntry("enabled", Dbus.bool(item.enabled())));
    }
    return Dbus.array("{sv}", properties);
  }

  private MemorySegment entryProperty(int id, String key) {
    TrayMenuItem item = this.entry(id);
    if (item == null) {
      return null;
    }
    return switch (key) {
      case "type" -> Dbus.string(item.isSeparator() ? "separator" : "standard");
      case "label" -> item.isSeparator() ? null : Dbus.string(item.label());
      case "enabled" -> Dbus.bool(item.enabled());
      case "visible" -> Dbus.bool(true);
      default -> null;
    };
  }

  private TrayMenuItem entry(int id) {
    List<TrayMenuItem> items = this.menu;
    return id >= 1 && id <= items.size() ? items.get(id - 1) : null;
  }

  private void menuEvent(int id, String event) {
    TrayMenuItem item = this.entry(id);
    if ("clicked".equals(event) && item != null && !item.isSeparator() && item.enabled()) {
      HandlerUtil.runOffTheUiThread(item.action());
    }
  }

  private static MemorySegment noPixmaps() {
    return Dbus.array("(iiay)", List.of());
  }

  /** {@code a(iiay)} with the one image. */
  private static MemorySegment pixmaps(Pixmap image) {
    return Dbus.array(
        "(iiay)",
        List.of(
            Dbus.tuple(
                List.of(
                    Dbus.int32(image.width()),
                    Dbus.int32(image.height()),
                    Dbus.bytes(image.argb())))));
  }

  // --- the vtable callbacks, bound by name from the upcall stubs above; signatures are GDBus's ---

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the tray is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the tray and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
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
      StatusNotifierTray tray = TRAYS.lookup(userData);
      String method = NativeLibraries.string(methodName);
      if (tray == null) {
        Dbus.returnValue(invocation, Dbus.tuple(List.of()));
      } else if (ITEM_INTERFACE.equals(NativeLibraries.string(interfaceName))) {
        tray.itemCall(method, invocation);
      } else {
        tray.menuCall(method, parameters, invocation);
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method. {@code
   * resource}: the tray is {@code AutoCloseable}, and a lookup that returns it looks like an
   * unclosed resource. It is not: the application owns the tray and closes it, this method only
   * borrows it.
   */
  @SuppressWarnings({"unused", "resource"})
  private static MemorySegment onGetProperty(
      MemorySegment connection,
      MemorySegment sender,
      MemorySegment objectPath,
      MemorySegment interfaceName,
      MemorySegment propertyName,
      MemorySegment error,
      MemorySegment userData) {
    try {
      StatusNotifierTray tray = TRAYS.lookup(userData);
      if (tray == null) {
        return MemorySegment.NULL;
      }
      String property = NativeLibraries.string(propertyName);
      MemorySegment value =
          ITEM_INTERFACE.equals(NativeLibraries.string(interfaceName))
              ? tray.itemProperty(property)
              : tray.menuProperty(property);
      return value == null ? MemorySegment.NULL : value;
    } catch (Throwable t) {
      ThrowableUtil.report(t);
      return MemorySegment.NULL;
    }
  }
}
