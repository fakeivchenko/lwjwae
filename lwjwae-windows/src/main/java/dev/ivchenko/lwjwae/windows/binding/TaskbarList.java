package dev.ivchenko.lwjwae.windows.binding;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import lombok.experimental.UtilityClass;

/**
 * Bindings to {@code ITaskbarList3} of the shell: the progress and the overlay icon of the button
 * of a window in the taskbar.
 */
@UtilityClass
public class TaskbarList {
  private final MemorySegment CLSID_TASKBAR_LIST = Com.guid("56fdf344-fd6d-11d0-958a-006097c9a090");
  private final MemorySegment IID_TASKBAR_LIST_3 = Com.guid("ea1afb91-9e28-4b86-90e9-9e9f8a5eefaf");

  // ITaskbarList, ITaskbarList2, ITaskbarList3
  private final int HR_INIT = 3;
  private final int SET_PROGRESS_VALUE = 9;
  private final int SET_PROGRESS_STATE = 10;
  private final int SET_OVERLAY_ICON = 18;

  /** {@code TBPF_*}: the states of the progress of a button. */
  public final int PROGRESS_NONE = 0;

  public final int PROGRESS_INDETERMINATE = 1;
  public final int PROGRESS_NORMAL = 2;
  public final int PROGRESS_ERROR = 4;
  public final int PROGRESS_PAUSED = 8;

  /** How finely the progress is told: the total of {@code SetProgressValue}. */
  private final long STEPS = 10_000;

  /**
   * A new {@code ITaskbarList3}, initialized, which the caller releases. Runs on a thread of the
   * single-threaded apartment.
   */
  public MemorySegment create() {
    MemorySegment list = Ole32.coCreateInstance(CLSID_TASKBAR_LIST, IID_TASKBAR_LIST_3);
    Com.check("HrInit", Com.call(list, HR_INIT));
    return list;
  }

  /**
   * Shows the progress {@code state}, a {@code TBPF_*}, and {@code value}, from 0 to 1, on the
   * button of {@code hwnd}.
   */
  public void setProgress(MemorySegment list, MemorySegment hwnd, int state, double value) {
    Com.check("SetProgressState", Com.call(list, SET_PROGRESS_STATE, hwnd, state));
    if (state == PROGRESS_NORMAL || state == PROGRESS_PAUSED || state == PROGRESS_ERROR) {
      Com.check(
          "SetProgressValue",
          Com.call(list, SET_PROGRESS_VALUE, hwnd, Math.round(value * STEPS), STEPS));
    }
  }

  /**
   * Puts {@code icon} over the button of {@code hwnd}, with {@code description} for a screen
   * reader, or takes it away for {@code NULL}. The taskbar keeps a copy of the icon.
   */
  public void setOverlayIcon(
      MemorySegment list, MemorySegment hwnd, MemorySegment icon, String description) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment text =
          description == null ? MemorySegment.NULL : Wide.allocate(arena, description);
      Com.check("SetOverlayIcon", Com.call(list, SET_OVERLAY_ICON, hwnd, icon, text));
    }
  }
}
