package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.AbstractApplication;
import dev.ivchenko.lwjwae.AbstractWindow;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.Screen;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.clipboard.Clipboard;
import dev.ivchenko.lwjwae.macos.binding.AppKit;
import dev.ivchenko.lwjwae.macos.binding.WebKit;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.tray.Tray;
import dev.ivchenko.lwjwae.tray.TrayIcon;
import java.util.List;
import java.util.function.Consumer;

/**
 * An application backed by Cocoa and WebKit, driven through the Objective-C runtime.
 *
 * <p>Cocoa runs its application loop on the main thread of the process, which the library doesn't
 * own. {@link MacDispatcher} starts the loop from a background thread when it can; when the process
 * is already on the main thread, as the {@code main} method of a native image is, {@link #run()}
 * runs the loop itself and stops it once the last window closed. Each window is a {@link
 * MacWindow}, each tray icon a {@link MacTray}, and each notification a {@link MacNotification}.
 * {@link MacMainMenu} gives the process its menu bar, and turns Quit into {@link #quit()}.
 */
public class MacApplication extends AbstractApplication {
  private volatile boolean runningApplication;
  private MacNotifier notifier;

  /** Creates an application with {@link ApplicationParameters#createDefault()}. */
  public MacApplication() {
    this(ApplicationParameters.createDefault());
  }

  /**
   * Makes sure that {@code NSApplication} exists and has launched, with a menu bar. Opens no
   * window.
   */
  public MacApplication(ApplicationParameters parameters) {
    super(MacDispatcher.instance(), parameters);
    MacMainMenu.register(this);
    this.dispatcher().run(() -> MacMainMenu.install(parameters.name()));
  }

  @Override
  public String engine() {
    return this.dispatcher().call(() -> "WKWebView " + WebKit.version());
  }

  @Override
  protected Clipboard createClipboard() {
    return new MacClipboard(this.dispatcher());
  }

  @Override
  public List<Screen> screens() {
    return this.dispatcher().call(MacScreens::all);
  }

  @Override
  protected AbstractWindow createWindow(long id, WindowParameters parameters) {
    return new MacWindow(this, id, parameters);
  }

  @Override
  protected Tray createTray(TrayIcon icon, Consumer<Tray> closed) {
    return new MacTray(this.dispatcher(), icon, closed);
  }

  @Override
  protected void launchExternal(String url) {
    this.dispatcher().run(() -> AppKit.openUrl(url));
  }

  @Override
  protected NotificationHandle createNotification(
      Notification notification, Consumer<NotificationHandle> closed) {
    return this.notifier().show(notification, closed);
  }

  /** The notifier, created on the first notification rather than at startup. */
  private synchronized MacNotifier notifier() {
    if (this.notifier == null) {
      this.notifier = new MacNotifier(this.dispatcher());
    }
    return this.notifier;
  }

  @Override
  protected void onClose() {
    MacMainMenu.unregister(this);
    MacNotifier current;
    synchronized (this) {
      current = this.notifier;
      this.notifier = null;
    }
    if (current != null) {
      current.close();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>On the main thread of a process that hasn't started its application loop yet, such as the
   * {@code main} method of a native image, this call runs the loop, and it returns when the last
   * window closes or on {@link #quit()}. Everywhere else, the loop is already running on the main
   * thread, and the caller only waits.
   */
  @Override
  public void run() {
    MacDispatcher dispatcher = (MacDispatcher) this.dispatcher();
    if (!dispatcher.isDispatchThread() || dispatcher.isApplicationRunning()) {
      super.run();
      return;
    }
    if (!this.isRunnable()) {
      return;
    }
    this.runningApplication = true;
    dispatcher.runApplication();
  }

  @Override
  protected void onIdle() {
    if (this.runningApplication) {
      this.runningApplication = false;
      AppKit.stopRunLoop();
    }
  }
}
