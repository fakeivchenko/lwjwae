package dev.ivchenko.lwjwae.glib;

import dev.ivchenko.lwjwae.glib.binding.Dbus;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.taskbar.ProgressState;
import dev.ivchenko.lwjwae.taskbar.TaskbarProgress;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;

/**
 * The progress and the count on the icon of the application in a dock or a task manager of Linux,
 * through the {@code com.canonical.Unity.LauncherEntry} signal of D-Bus.
 *
 * <p>Unity made the signal up, and it outlived Unity: the task manager of KDE Plasma, the dock of
 * Ubuntu, Dash to Dock, and Plank all listen to it. It names the application by the URI of its
 * desktop entry, {@code application://NAME.desktop}, and the dock finds the icon of that entry. The
 * entry is named after the program, as the Gradle plugin of lwjwae installs it, so a program
 * started as {@code notes} speaks for {@code notes.desktop}. Under the {@code java} launcher,
 * there's no entry of that name, and the signal reaches no icon.
 *
 * <p>A dock keeps what it heard last, so every signal carries the whole state: the progress and the
 * count together. Without a session bus, there's no dock to tell, and nothing happens.
 */
public final class LauncherEntry {
  private static final String INTERFACE = "com.canonical.Unity.LauncherEntry";
  private static final String PATH = "/dev/ivchenko/lwjwae/LauncherEntry";

  private final String uri;
  private TaskbarProgress progress = TaskbarProgress.none();
  private long count;

  /** The entry of {@code desktopId}, such as {@code notes.desktop}. */
  public LauncherEntry(String desktopId) {
    this.uri = "application://" + desktopId;
  }

  /** The entry named after the executable of this process. */
  public static LauncherEntry ofThisProcess() {
    String command = ProcessHandle.current().info().command().orElse("application");
    return new LauncherEntry(Path.of(command).getFileName() + ".desktop");
  }

  /** The URI that the signal names the application by. */
  public String uri() {
    return this.uri;
  }

  /** Shows {@code progress}. */
  public synchronized void progress(TaskbarProgress progress) {
    this.progress = progress;
    this.update();
  }

  /** Shows {@code count}, 0 for none. */
  public synchronized void count(long count) {
    this.count = count;
    this.update();
  }

  private void update() {
    MemorySegment bus;
    try {
      bus = Dbus.sessionBus();
    } catch (IllegalStateException _) {
      // No session bus: no dock either.
      return;
    }
    try {
      boolean bar = this.progress.isShown() && this.progress.state() != ProgressState.INDETERMINATE;
      Dbus.emitSignal(
          bus,
          PATH,
          INTERFACE,
          "Update",
          Dbus.tuple(
              List.of(
                  Dbus.string(this.uri),
                  Dbus.array(
                      "{sv}",
                      List.of(
                          Dbus.dictEntry("count", Dbus.int64(this.count)),
                          Dbus.dictEntry("count-visible", Dbus.bool(this.count > 0)),
                          Dbus.dictEntry("progress", Dbus.float64(this.progress.value())),
                          Dbus.dictEntry("progress-visible", Dbus.bool(bar)),
                          Dbus.dictEntry(
                              "urgent",
                              Dbus.bool(this.progress.state() == ProgressState.ERROR)))))));
    } finally {
      Glib.unref(bus);
    }
  }
}
