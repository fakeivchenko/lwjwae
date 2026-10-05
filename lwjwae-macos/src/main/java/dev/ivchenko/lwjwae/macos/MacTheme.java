package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.MethodStub;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.macos.binding.Signatures;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Follows the appearance of macOS, light or dark, which the user sets in System Settings and which
 * the system may switch itself at sunset.
 *
 * <p>The choice is the {@code AppleInterfaceStyle} of the global defaults: {@code Dark} in dark
 * mode, and no value in light mode. The system tells every process of a change with the distributed
 * notification {@code AppleInterfaceThemeChangedNotification}. It posts that before the defaults of
 * the process have caught up, so the observer reads again a moment later, and a theme that the
 * application already has changes nothing.
 *
 * <p>Everything here runs on the main thread, where the notification arrives.
 */
final class MacTheme {
  private static final String NOTIFICATION = "AppleInterfaceThemeChangedNotification";
  private static final String STYLE_KEY = "AppleInterfaceStyle";
  private static final long RECHECK_MILLIS = 300;

  private static final Map<Long, MacTheme> OBSERVERS = new ConcurrentHashMap<>();
  private static final MemorySegment ON_CHANGED =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacTheme.class,
          "onChanged",
          MethodType.methodType(
              void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.DELEGATE_1);
  private static final MemorySegment OBSERVER_CLASS =
      ObjC.defineClass(
          "LwjwaeThemeObserver",
          ObjC.cls("NSObject"),
          Map.of("themeChanged:", new MethodStub(ON_CHANGED, "v@:@")));

  private final UiDispatcher dispatcher;
  private final Consumer<SystemTheme> listener;
  private final MemorySegment observer;

  /**
   * Reports the theme now, then at every change. Runs on the main thread.
   *
   * @param listener Receives the theme on the main thread.
   */
  MacTheme(UiDispatcher dispatcher, Consumer<SystemTheme> listener) {
    this.dispatcher = dispatcher;
    this.listener = listener;
    this.observer = ObjC.send(ObjC.send(OBSERVER_CLASS, "alloc"), "init");
    OBSERVERS.put(this.observer.address(), this);
    MemorySegment center = ObjC.send(ObjC.cls("NSDistributedNotificationCenter"), "defaultCenter");
    ObjC.sendVoid(
        center,
        "addObserver:selector:name:object:",
        this.observer,
        ObjC.sel("themeChanged:"),
        Foundation.string(NOTIFICATION),
        MemorySegment.NULL);
    this.listener.accept(MacTheme.read());
  }

  /** The appearance of the system now. Runs on the main thread. */
  static SystemTheme read() {
    MemorySegment defaults = ObjC.send(ObjC.cls("NSUserDefaults"), "standardUserDefaults");
    String style =
        Foundation.string(ObjC.send(defaults, "stringForKey:", Foundation.string(STYLE_KEY)));
    return "Dark".equalsIgnoreCase(style) ? SystemTheme.DARK : SystemTheme.LIGHT;
  }

  /** Stops listening. Runs on the main thread. */
  void close() {
    OBSERVERS.remove(this.observer.address());
    MemorySegment center = ObjC.send(ObjC.cls("NSDistributedNotificationCenter"), "defaultCenter");
    ObjC.sendVoid(center, "removeObserver:", this.observer);
  }

  private void refresh() {
    this.listener.accept(MacTheme.read());
  }

  /**
   * The system switched. The value of the defaults may come after the notification, so this reads
   * now and again {@link #RECHECK_MILLIS} later.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private static void onChanged(MemorySegment self, MemorySegment command, MemorySegment note) {
    try {
      MacTheme theme = OBSERVERS.get(self.address());
      if (theme == null) {
        return;
      }
      theme.refresh();
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  Thread.sleep(RECHECK_MILLIS);
                  theme.dispatcher.post(
                      () -> {
                        if (OBSERVERS.containsKey(self.address())) {
                          theme.refresh();
                        }
                      });
                } catch (InterruptedException _) {
                  // The application is going away.
                }
              });
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
