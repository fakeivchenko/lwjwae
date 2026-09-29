package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.taskbar.ProgressState;
import dev.ivchenko.lwjwae.taskbar.TaskbarProgress;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import lombok.experimental.UtilityClass;

/**
 * The icon of the process in the Dock: its badge, and a progress bar drawn over it.
 *
 * <p>The badge is the {@code badgeLabel} of {@code NSDockTile}. A bar isn't: the tile draws a view
 * of the application instead of its icon while it has one, so the progress is an image view of the
 * icon with an {@code NSProgressIndicator} along its bottom, and the tile draws it again on {@code
 * display} after every change, which is the only time a tile draws. An indeterminate bar doesn't
 * move for the same reason. Every call runs on the main thread.
 */
@UtilityClass
class MacDock {
  /** The view of the tile while it shows a bar, or {@code null}. */
  private MemorySegment view;

  private MemorySegment indicator;

  /** Shows {@code count} as the badge, or none for 0. */
  void badge(int count) {
    ObjC.sendVoid(
        MacDock.tile(),
        "setBadgeLabel:",
        count == 0 ? MemorySegment.NULL : Foundation.string(Integer.toString(count)));
  }

  /** Shows {@code progress} over the icon, or the icon alone for none. */
  void progress(TaskbarProgress progress) {
    MemorySegment tile = MacDock.tile();
    if (!progress.isShown()) {
      if (view != null) {
        ObjC.sendVoid(tile, "setContentView:", MemorySegment.NULL);
        Foundation.release(indicator);
        Foundation.release(view);
        view = null;
        indicator = null;
      }
    } else {
      if (view == null) {
        MacDock.createView(tile);
      }
      boolean indeterminate = progress.state() == ProgressState.INDETERMINATE;
      ObjC.sendVoid(indicator, "setIndeterminate:", indeterminate);
      if (!indeterminate) {
        MemorySegment _ = ObjC.send(indicator, "setDoubleValue:", progress.value());
      }
    }
    ObjC.sendVoid(tile, "display");
  }

  private void createView(MemorySegment tile) {
    double[] size = ObjC.sendPoint(tile, "size");
    try (Arena arena = Arena.ofConfined()) {
      view =
          ObjC.sendWithRect(
              ObjC.send(ObjC.cls("NSImageView"), "alloc"),
              "initWithFrame:",
              Foundation.rect(arena, 0, 0, size[0], size[1]));
      ObjC.sendVoid(view, "setImage:", ObjC.send(AppKit.application(), "applicationIconImage"));
      indicator =
          ObjC.sendWithRect(
              ObjC.send(ObjC.cls("NSProgressIndicator"), "alloc"),
              "initWithFrame:",
              Foundation.rect(arena, size[0] * 0.1, size[1] * 0.06, size[0] * 0.8, size[1] * 0.14));
    }
    // NSProgressIndicatorStyleBar, from 0 to 1.
    ObjC.sendVoid(indicator, "setStyle:", 0L);
    MemorySegment _ = ObjC.send(indicator, "setMinValue:", 0.0);
    MemorySegment _ = ObjC.send(indicator, "setMaxValue:", 1.0);
    ObjC.sendVoid(view, "addSubview:", indicator);
    ObjC.sendVoid(tile, "setContentView:", view);
  }

  private MemorySegment tile() {
    return ObjC.send(AppKit.application(), "dockTile");
  }
}
