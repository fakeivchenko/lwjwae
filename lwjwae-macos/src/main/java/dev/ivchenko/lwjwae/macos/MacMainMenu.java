package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.MethodStub;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.macos.binding.Signatures;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;

/**
 * The menu bar of the process, and the way a request to quit reaches the applications.
 *
 * <p>On macOS, the keyboard shortcuts of editing live in the menu bar, not in the views: Command-C
 * is the key equivalent of an item that sends {@code copy:} to the first responder, and a {@code
 * WKWebView} that the page leaves the key to hands it back to the menu. Without a menu bar, a text
 * field on a page takes no Command-C, V, X, A, or Z, and Command-Q quits nothing. So the first
 * application installs the menu bar that every Mac application has: the application menu, File,
 * Edit, and Window. Every item has no target and goes along the responder chain, which also enables
 * and disables it; nothing here runs Java code, except for quitting.
 *
 * <p>Quit, whether from the menu, the Dock, or a logout, is {@code terminate:}, which asks {@code
 * applicationShouldTerminate:} of the delegate of {@code NSApplication} and then calls {@code
 * exit}, under the JVM. The delegate cancels that and quits the applications instead, which closes
 * their windows the way {@link MacApplication#quit()} always does, so {@code run()} returns and the
 * program ends as it ends when the last window closes. With no application open, it lets AppKit
 * terminate: there is nothing left to close.
 *
 * <p>The menu bar replaces the one that AppKit makes up when {@code run} starts without one, which
 * holds nothing but the application menu. A delegate that something else set first stays.
 */
@UtilityClass
class MacMainMenu {
  private final long TERMINATE_CANCEL = 0;
  private final long TERMINATE_NOW = 1;
  private final Set<MacApplication> APPLICATIONS = ConcurrentHashMap.newKeySet();
  private final MemorySegment SHOULD_TERMINATE_STUB =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacMainMenu.class,
          "onShouldTerminate",
          MethodType.methodType(
              long.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
          Signatures.DELEGATE_1_LONG);

  /** The delegate of {@code NSApplication}, which holds it weakly. Main thread only. */
  private MemorySegment delegate;

  /** Whether the menu bar is in place. Main thread only. */
  private boolean menuBarInstalled;

  /**
   * Installs the menu bar, with {@code name} in the titles of the application menu, once, and the
   * delegate, unless something set one before. Runs on the main thread.
   *
   * @param name The name of the application, or {@code null} for the name of the process.
   */
  void install(String name) {
    if (delegate == null && ObjC.isNull(ObjC.send(AppKit.application(), "delegate"))) {
      delegate =
          ObjC.send(
              ObjC.send(
                  ObjC.defineClass(
                      "LwjwaeApplicationDelegate",
                      ObjC.cls("NSObject"),
                      Map.of(
                          "applicationShouldTerminate:",
                          new MethodStub(SHOULD_TERMINATE_STUB, "Q@:@"))),
                  "alloc"),
              "init");
      AppKit.setApplicationDelegate(delegate);
    }
    if (!menuBarInstalled) {
      menuBarInstalled = true;
      MacMainMenu.installMenuBar(name != null ? name : Foundation.processName());
    }
  }

  /** Makes {@code application} one that a request to quit quits, until it closes. */
  void register(MacApplication application) {
    APPLICATIONS.add(application);
  }

  /** Forgets {@code application}: it has closed. */
  void unregister(MacApplication application) {
    APPLICATIONS.remove(application);
  }

  private void installMenuBar(String name) {
    long command = AppKit.MODIFIER_COMMAND;
    long optionCommand = AppKit.MODIFIER_OPTION | AppKit.MODIFIER_COMMAND;

    MemorySegment applicationMenu = AppKit.autoenabledMenu(name);
    AppKit.addResponderItem(
        applicationMenu, "About " + name, "orderFrontStandardAboutPanel:", "", 0);
    AppKit.addMenuSeparator(applicationMenu);
    AppKit.addResponderItem(applicationMenu, "Hide " + name, "hide:", "h", command);
    AppKit.addResponderItem(
        applicationMenu, "Hide Others", "hideOtherApplications:", "h", optionCommand);
    AppKit.addResponderItem(applicationMenu, "Show All", "unhideAllApplications:", "", 0);
    AppKit.addMenuSeparator(applicationMenu);
    AppKit.addResponderItem(applicationMenu, "Quit " + name, "terminate:", "q", command);

    MemorySegment fileMenu = AppKit.autoenabledMenu("File");
    AppKit.addResponderItem(fileMenu, "Close Window", "performClose:", "w", command);

    MemorySegment editMenu = AppKit.autoenabledMenu("Edit");
    AppKit.addResponderItem(editMenu, "Undo", "undo:", "z", command);
    AppKit.addResponderItem(editMenu, "Redo", "redo:", "Z", command);
    AppKit.addMenuSeparator(editMenu);
    AppKit.addResponderItem(editMenu, "Cut", "cut:", "x", command);
    AppKit.addResponderItem(editMenu, "Copy", "copy:", "c", command);
    AppKit.addResponderItem(editMenu, "Paste", "paste:", "v", command);
    AppKit.addResponderItem(
        editMenu, "Paste and Match Style", "pasteAsPlainText:", "V", optionCommand);
    AppKit.addResponderItem(editMenu, "Delete", "delete:", "", 0);
    AppKit.addResponderItem(editMenu, "Select All", "selectAll:", "a", command);

    MemorySegment windowMenu = AppKit.autoenabledMenu("Window");
    AppKit.addResponderItem(windowMenu, "Minimize", "performMiniaturize:", "m", command);
    AppKit.addResponderItem(windowMenu, "Zoom", "performZoom:", "", 0);
    AppKit.addMenuSeparator(windowMenu);
    AppKit.addResponderItem(windowMenu, "Bring All to Front", "arrangeInFront:", "", 0);

    MemorySegment bar = AppKit.autoenabledMenu("");
    for (MemorySegment menu : List.of(applicationMenu, fileMenu, editMenu, windowMenu)) {
      AppKit.addSubmenu(bar, menu);
    }
    AppKit.setMainMenu(bar, windowMenu);
  }

  /**
   * {@code applicationShouldTerminate:}: quits the open applications instead of letting AppKit exit
   * the process, or lets it when none is open.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private long onShouldTerminate(MemorySegment self, MemorySegment command, MemorySegment sender) {
    List<MacApplication> open = List.copyOf(APPLICATIONS);
    if (open.isEmpty()) {
      return TERMINATE_NOW;
    }
    for (MacApplication application : open) {
      try {
        application.quit();
      } catch (Throwable t) {
        ThrowableUtil.report(t);
      }
    }
    return TERMINATE_CANCEL;
  }
}
