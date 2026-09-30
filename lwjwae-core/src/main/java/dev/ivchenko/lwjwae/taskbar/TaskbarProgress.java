package dev.ivchenko.lwjwae.taskbar;

/**
 * The progress that the icon of the application shows in the taskbar or the Dock.
 *
 * @param state What the progress says. {@code null} is {@link ProgressState#NONE}.
 * @param value How far the work is done, from 0 to 1. A value outside that is clamped into it, and
 *     one that isn't a number counts as 0. Ignored for {@link ProgressState#NONE} and {@link
 *     ProgressState#INDETERMINATE}.
 */
public record TaskbarProgress(ProgressState state, double value) {
  public TaskbarProgress {
    if (state == null) {
      state = ProgressState.NONE;
    }
    value = Double.isNaN(value) ? 0 : Math.clamp(value, 0.0, 1.0);
  }

  /** No progress. */
  public static TaskbarProgress none() {
    return new TaskbarProgress(ProgressState.NONE, 0);
  }

  /** Work that is {@code value}, from 0 to 1, done. */
  public static TaskbarProgress of(double value) {
    return new TaskbarProgress(ProgressState.NORMAL, value);
  }

  /** Work of a length that nobody knows yet. */
  public static TaskbarProgress indeterminate() {
    return new TaskbarProgress(ProgressState.INDETERMINATE, 0);
  }

  /** Work that stopped at {@code value} for now. */
  public static TaskbarProgress paused(double value) {
    return new TaskbarProgress(ProgressState.PAUSED, value);
  }

  /** Work that failed at {@code value}. */
  public static TaskbarProgress error(double value) {
    return new TaskbarProgress(ProgressState.ERROR, value);
  }

  /** Whether the icon shows a bar: every state but {@link ProgressState#NONE}. */
  public boolean isShown() {
    return this.state != ProgressState.NONE;
  }
}
