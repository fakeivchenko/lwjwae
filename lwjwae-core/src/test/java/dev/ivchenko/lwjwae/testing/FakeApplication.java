package dev.ivchenko.lwjwae.testing;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
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
    super(new FakeUiDispatcher(), parameters);
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
  public String engine() {
    return "fake 0";
  }
}
