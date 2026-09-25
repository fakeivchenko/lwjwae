package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * An {@link AbstractApplication} without a toolkit: every window it opens is a {@link FakeWindow}
 * that records what the base classes ask of it.
 */
public class FakeApplication extends AbstractApplication {
  public FakeApplication() {
    this(ApplicationParameters.createDefault());
  }

  public FakeApplication(ApplicationParameters parameters) {
    super(FakeApplication.started(new FakeUiDispatcher()), parameters);
  }

  /** A backend starts its UI thread before the application; the fake does the same. */
  private static FakeUiDispatcher started(FakeUiDispatcher dispatcher) {
    dispatcher.start();
    return dispatcher;
  }

  /** Opens a window and returns it as the fake, so a test needs no cast. */
  public FakeWindow openFake() {
    return (FakeWindow) this.open();
  }

  /** Opens a window and returns it as the fake, so a test needs no cast. */
  public FakeWindow openFake(WindowParameters parameters) {
    return (FakeWindow) this.open(parameters);
  }

  @Override
  protected AbstractWindow createWindow(long id, WindowParameters parameters) {
    return new FakeWindow(this, id, parameters);
  }

  @Override
  protected Tray createTray(TrayIcon icon, Consumer<Tray> closed) {
    return new FakeTray(closed);
  }

  @Override
  protected NotificationHandle createNotification(
      Notification notification, Consumer<NotificationHandle> closed) {
    return new FakeNotification(closed);
  }

  /** Every URL that went to the system, in order. */
  public final List<String> launched = new CopyOnWriteArrayList<>();

  @Override
  protected void launchExternal(String url) {
    this.launched.add(url);
  }

  @Override
  public String engine() {
    return "fake 0";
  }
}
