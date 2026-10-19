package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.taskbar.Taskbar;
import dev.ivchenko.lwjwae.taskbar.TaskbarProgress;
import java.util.Objects;

/**
 * The taskbar of one application: what its icon shows, which the backend puts on the icon on the UI
 * thread.
 *
 * <p>The progress and the count are kept here, not read back from the platform, because some
 * platforms show them per window and can't tell them: a backend reads them for a window that opens
 * later.
 */
final class ApplicationTaskbar implements Taskbar {
  private final AbstractApplication application;
  private volatile TaskbarProgress progress = TaskbarProgress.none();
  private volatile int badgeCount;

  ApplicationTaskbar(AbstractApplication application) {
    this.application = application;
  }

  @Override
  public void progress(TaskbarProgress progress) {
    Objects.requireNonNull(progress, "progress");
    this.application.checkOpen();
    this.progress = progress;
    this.application.dispatcher().run(() -> this.application.showProgress(progress));
  }

  @Override
  public TaskbarProgress progress() {
    return this.progress;
  }

  @Override
  public void badgeCount(int count) {
    if (count < 0) {
      throw new IllegalArgumentException("A badge counts from 0: " + count);
    }
    this.application.checkOpen();
    this.badgeCount = count;
    this.application.dispatcher().run(() -> this.application.showBadgeCount(count));
  }

  @Override
  public int badgeCount() {
    return this.badgeCount;
  }
}
