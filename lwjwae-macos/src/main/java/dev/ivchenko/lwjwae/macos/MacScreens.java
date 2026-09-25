package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.ScreenArea;
import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The screens of AppKit as {@link Screen}s. Every function here runs on the main thread.
 *
 * <p>AppKit measures in points from the bottom left of the primary screen; the areas are turned to
 * the top left as a window's place is. The visible frame leaves out the menu bar and the Dock,
 * which is the work area.
 */
@UtilityClass
class MacScreens {
  /** Every screen, the primary one first, as AppKit lists them. */
  List<Screen> all() {
    List<Screen> screens = new ArrayList<>();
    for (MemorySegment screen : AppKit.screens()) {
      screens.add(MacScreens.of(screen, screens.isEmpty()));
    }
    return screens;
  }

  /** The screen of {@code window}, or the primary one for a window off every screen. */
  Screen of(MemorySegment window) {
    List<MemorySegment> screens = AppKit.screens();
    MemorySegment screen = AppKit.screenOf(window);
    if (ObjC.isNull(screen)) {
      return MacScreens.of(screens.getFirst(), true);
    }
    return MacScreens.of(screen, screen.address() == screens.getFirst().address());
  }

  private Screen of(MemorySegment screen, boolean primary) {
    return new Screen(
        AppKit.screenName(screen),
        MacScreens.area(AppKit.screenArea(screen, false)),
        MacScreens.area(AppKit.screenArea(screen, true)),
        AppKit.backingScaleFactor(screen),
        primary);
  }

  private ScreenArea area(int[] rectangle) {
    return new ScreenArea(rectangle[0], rectangle[1], rectangle[2], rectangle[3]);
  }
}
