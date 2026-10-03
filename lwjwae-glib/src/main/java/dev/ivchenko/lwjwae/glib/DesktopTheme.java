package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Dbus;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.binding.Signatures;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Follows the light or dark choice of the user on Linux, where it lives in more than one place.
 *
 * <p>The desktop portal has the {@code color-scheme} of the user, which GNOME, KDE Plasma, and the
 * other desktops that ship a portal keep up to date; a session without a portal has only the
 * settings of GTK, where a dark theme shows as the name of the theme or as the dark preference. The
 * portal wins where it has a choice. GTK 3 doesn't read it, and the engine of the web view takes
 * its {@code prefers-color-scheme} from the dark preference of GTK, so this class writes the choice
 * of the portal there: a page then matches what the desktop shows, and the title bar too.
 *
 * <p>Everything runs on the GTK thread, where the signals of D-Bus and of GTK arrive.
 */
public final class DesktopTheme implements AutoCloseable {
  private static final String SERVICE = "org.freedesktop.portal.Desktop";
  private static final String OBJECT_PATH = "/org/freedesktop/portal/desktop";
  private static final String INTERFACE = "org.freedesktop.portal.Settings";
  private static final String APPEARANCE = "org.freedesktop.appearance";
  private static final String COLOR_SCHEME = "color-scheme";
  private static final String DARK_PREFERENCE = "gtk-application-prefer-dark-theme";
  private static final String THEME_NAME = "gtk-theme-name";
  private static final int CALL_TIMEOUT_MILLIS = 2000;

  /** {@code color-scheme} of the portal: no preference, prefer dark, prefer light. */
  private static final int SCHEME_DARK = 1;

  private static final int SCHEME_LIGHT = 2;

  private static final CallbackRegistry<DesktopTheme> THEMES = new CallbackRegistry<>();

  private static final MemorySegment ON_SIGNAL =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          DesktopTheme.class,
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
  private static final MemorySegment ON_NOTIFY =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          DesktopTheme.class,
          "onNotify",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.VOID_POINTER_POINTER_POINTER);

  private final MemorySegment settings;
  private final Consumer<SystemTheme> listener;
  private final long callbackId;
  private final MemorySegment bus;
  private final int subscription;

  /** {@code color-scheme} of the portal, or {@code 0} without a portal or a preference. */
  private int scheme;

  private boolean closed;

  /**
   * Reads the theme and starts to follow it, on the GTK thread. {@code listener} runs on that
   * thread, first before this constructor returns.
   *
   * @param settings The {@code GtkSettings} of the default screen.
   * @param listener Receives the theme now, and again at every change.
   */
  public DesktopTheme(MemorySegment settings, Consumer<SystemTheme> listener) {
    this.settings = settings;
    this.listener = listener;
    this.callbackId = THEMES.register(this);
    MemorySegment connection = null;
    int id = 0;
    try {
      connection = Dbus.sessionBus();
      this.scheme = DesktopTheme.readScheme(connection);
      id =
          Dbus.subscribe(
              connection,
              SERVICE,
              INTERFACE,
              OBJECT_PATH,
              ON_SIGNAL,
              CallbackRegistry.userData(this.callbackId));
    } catch (IllegalStateException _) {
      // No session bus, or no portal on it: the settings of GTK are all there is.
      if (connection != null) {
        Glib.unref(connection);
        connection = null;
      }
    }
    this.bus = connection;
    this.subscription = id;
    for (String property : List.of(DARK_PREFERENCE, THEME_NAME)) {
      Glib.signalConnect(
          settings, "notify::" + property, ON_NOTIFY, CallbackRegistry.userData(this.callbackId));
    }
    this.refresh();
  }

  /** {@code color-scheme} of the portal, {@code 0} for no preference or for no portal. */
  private static int readScheme(MemorySegment connection) {
    MemorySegment reply;
    try {
      reply =
          Dbus.call(
              connection,
              SERVICE,
              OBJECT_PATH,
              INTERFACE,
              "Read",
              Dbus.tuple(List.of(Dbus.string(APPEARANCE), Dbus.string(COLOR_SCHEME))),
              "(v)",
              CALL_TIMEOUT_MILLIS);
    } catch (IllegalStateException _) {
      // The portal has no such setting, or no portal runs.
      return 0;
    }
    try {
      return Dbus.wrappedUint32At(reply, 0);
    } finally {
      Dbus.unref(reply);
    }
  }

  /**
   * The theme that the portal chose, or that GTK shows, and the preference of GTK made to match.
   */
  private void refresh() {
    if (this.closed) {
      return;
    }
    SystemTheme theme;
    if (this.scheme == SCHEME_DARK || this.scheme == SCHEME_LIGHT) {
      theme = this.scheme == SCHEME_DARK ? SystemTheme.DARK : SystemTheme.LIGHT;
      boolean dark = theme == SystemTheme.DARK;
      if (Glib.booleanProperty(this.settings, DARK_PREFERENCE) != dark) {
        Glib.setBooleanProperty(this.settings, DARK_PREFERENCE, dark);
      }
    } else {
      String name = Glib.stringProperty(this.settings, THEME_NAME);
      boolean dark =
          Glib.booleanProperty(this.settings, DARK_PREFERENCE)
              || (name != null && name.toLowerCase(Locale.ROOT).contains("dark"));
      theme = dark ? SystemTheme.DARK : SystemTheme.LIGHT;
    }
    this.listener.accept(theme);
  }

  /** Stops following the desktop. */
  @Override
  public void close() {
    this.closed = true;
    THEMES.unregister(this.callbackId);
    if (this.bus != null) {
      Dbus.unsubscribe(this.bus, this.subscription);
      Glib.unref(this.bus);
    }
  }

  /**
   * {@code SettingChanged} of the portal: the user switched, or the desktop did, as at sunset.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
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
      DesktopTheme theme = THEMES.lookup(userData);
      if (theme == null
          || !"SettingChanged".equals(NativeLibraries.string(signalName))
          || !APPEARANCE.equals(Dbus.stringAt(parameters, 0))
          || !COLOR_SCHEME.equals(Dbus.stringAt(parameters, 1))) {
        return;
      }
      theme.scheme = Dbus.wrappedUint32At(parameters, 2);
      theme.refresh();
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }

  /**
   * A property of GTK that tells dark from light changed.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onNotify(
      MemorySegment settings, MemorySegment property, MemorySegment userData) {
    try {
      DesktopTheme theme = THEMES.lookup(userData);
      if (theme != null) {
        theme.refresh();
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
