package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import dev.ivchenko.lwjwae.windows.binding.Advapi32;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.ComCallback;
import dev.ivchenko.lwjwae.windows.binding.ComEvent;
import dev.ivchenko.lwjwae.windows.binding.Toasts;
import dev.ivchenko.lwjwae.windows.exception.ComCallFailedException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The notifications of one application, as toasts: the notifications of Windows 10 and 11, which
 * pop up in the corner of the screen and stay in the notification center afterwards.
 *
 * <p>Windows files toasts under an application user model ID (AUMID), and shows the name and the
 * icon registered for it. A packaged application has one from its manifest; a plain executable
 * registers one under {@code HKCU\Software\Classes\AppUserModelId}, which is what the notifier does
 * on its first use. The ID is {@code lwjwae.} plus the name of the application, so that two
 * applications keep their notifications apart. The key stays when the application exits, as it
 * must: the notification center still lists the toasts of the application after it's gone, and it
 * needs the name to label them.
 *
 * <p>The content of a toast is XML in the {@code ToastGeneric} template: two text lines, an image
 * as a {@code file:} URI in {@code appLogoOverride} placement, and one {@code action} per button.
 * The toast itself carries the argument {@code default} for a click on its body, and each button
 * {@code action-N}, as on Linux. The events of a toast fire on a thread of the pool, so the
 * handlers are agile COM objects, and they touch nothing that belongs to the UI thread.
 */
final class WindowsNotifier {
  private static final String REGISTRY_KEY = "Software\\Classes\\AppUserModelId\\";
  private static final String DEFAULT_ACTION = "default";
  private static final String BUTTON_ACTION_PREFIX = "action-";
  private static final AtomicLong IMAGE_IDS = new AtomicLong();

  private final UiDispatcher dispatcher;
  private final String applicationId;

  private volatile MemorySegment notifier;
  private volatile Path directory;

  /**
   * Registers the application ID and creates the toast notifier.
   *
   * @param applicationName The name that Windows shows on the toasts, or {@code null} for the name
   *     of the executable.
   */
  WindowsNotifier(UiDispatcher dispatcher, String applicationName) {
    this.dispatcher = dispatcher;
    String displayName = applicationName == null ? executableName() : applicationName;
    this.applicationId = "lwjwae." + displayName.replaceAll("[^A-Za-z0-9]+", ".");
    this.dispatcher.run(
        () -> {
          Advapi32.writeString(
              Advapi32.HKEY_CURRENT_USER,
              REGISTRY_KEY + this.applicationId,
              "DisplayName",
              displayName);
          this.notifier = Toasts.notifier(this.applicationId);
        });
  }

  /** The AppUserModelID that the toasts are filed under. */
  String applicationId() {
    return this.applicationId;
  }

  /**
   * Shows {@code notification} as a toast.
   *
   * @throws UnsupportedOperationException If Windows doesn't let the application show toasts.
   */
  WindowsNotification show(Notification notification, Consumer<NotificationHandle> closed) {
    Path image = notification.icon() == null ? null : this.writeImage(notification.icon());
    try {
      return this.dispatcher.call(() -> this.showNow(notification, image, closed));
    } catch (RuntimeException e) {
      deleteQuietly(image);
      throw e;
    }
  }

  private WindowsNotification showNow(
      Notification notification, Path image, Consumer<NotificationHandle> closed) {
    MemorySegment current = this.notifier();
    int setting = Toasts.setting(current);
    if (setting != Toasts.SETTING_ENABLED && setting != Toasts.SETTING_UNKNOWN) {
      throw new UnsupportedOperationException(
          "Windows doesn't let "
              + this.applicationId
              + " show notifications (setting "
              + setting
              + ")");
    }
    MemorySegment toast = Toasts.notification(xml(notification, image));
    try {
      WindowsNotification handle =
          new WindowsNotification(this, toast, notification, image, closed);
      subscribe(
          toast,
          Toasts.IID_ACTIVATED_HANDLER,
          Toasts::onActivated,
          (_, arguments) -> handle.activated(Toasts.activatedArguments(arguments)));
      subscribe(
          toast,
          Toasts.IID_DISMISSED_HANDLER,
          Toasts::onDismissed,
          (_, arguments) -> handle.dismissed(Toasts.dismissalReason(arguments)));
      subscribe(toast, Toasts.IID_FAILED_HANDLER, Toasts::onFailed, (_, _) -> handle.failed());
      Toasts.show(current, toast);
      return handle;
    } catch (ComCallFailedException e) {
      Com.release(toast);
      throw new UnsupportedOperationException(
          "Windows refused the notification: " + e.getMessage(), e);
    } catch (RuntimeException e) {
      Com.release(toast);
      throw e;
    }
  }

  /** Takes the toast of {@code notification} off the screen and out of the notification center. */
  void withdraw(WindowsNotification notification) {
    this.dispatcher.run(
        () -> {
          MemorySegment current = this.notifier;
          if (current != null) {
            // Fails for a toast that the user dismissed already, which is the outcome we want.
            int _ = Toasts.hide(current, notification.toast());
          }
        });
  }

  /** Releases the notifier. The notifications must be closed already. */
  void close() {
    this.dispatcher.run(
        () -> {
          MemorySegment current = this.notifier;
          this.notifier = null;
          Com.release(current);
        });
    deleteQuietly(this.directory);
  }

  private MemorySegment notifier() {
    MemorySegment current = this.notifier;
    if (current == null) {
      throw new IllegalStateException("The notifier is closed");
    }
    return current;
  }

  private static void subscribe(
      MemorySegment toast,
      MemorySegment iid,
      BiConsumer<MemorySegment, MemorySegment> subscription,
      ComEvent event) {
    MemorySegment handler =
        ComCallback.agileEvent(
            iid,
            (sender, arguments) -> {
              try {
                event.invoke(sender, arguments);
              } catch (Throwable t) {
                ThrowableUtil.report(t);
              }
            });
    try {
      subscription.accept(toast, handler);
    } finally {
      Com.release(handler);
    }
  }

  /** The toast XML of {@code notification}. */
  static String xml(Notification notification, Path image) {
    StringBuilder xml = new StringBuilder();
    xml.append("<toast launch=\"").append(DEFAULT_ACTION).append("\">");
    xml.append("<visual><binding template=\"ToastGeneric\">");
    xml.append("<text>").append(escape(notification.title())).append("</text>");
    if (notification.body() != null) {
      xml.append("<text>").append(escape(notification.body())).append("</text>");
    }
    if (image != null) {
      xml.append("<image placement=\"appLogoOverride\" src=\"")
          .append(escape(image.toUri().toString()))
          .append("\"/>");
    }
    xml.append("</binding></visual>");
    List<NotificationAction> actions = notification.actions();
    if (!actions.isEmpty()) {
      xml.append("<actions>");
      for (int index = 0; index < actions.size(); index++) {
        xml.append("<action content=\"")
            .append(escape(actions.get(index).label()))
            .append("\" arguments=\"")
            .append(BUTTON_ACTION_PREFIX)
            .append(index)
            .append("\"/>");
      }
      xml.append("</actions>");
    }
    return xml.append("</toast>").toString();
  }

  /** The number of the button that {@code arguments} names, or -1 for a click on the toast. */
  static int buttonOf(String arguments) {
    if (arguments.startsWith(BUTTON_ACTION_PREFIX)) {
      try {
        return Integer.parseInt(arguments.substring(BUTTON_ACTION_PREFIX.length()));
      } catch (NumberFormatException _) {
        return -1;
      }
    }
    return -1;
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private static String executableName() {
    String name =
        ProcessHandle.current()
            .info()
            .command()
            .map(command -> Path.of(command).getFileName().toString())
            .orElse("lwjwae");
    return name.toLowerCase().endsWith(".exe") ? name.substring(0, name.length() - 4) : name;
  }

  private synchronized Path writeImage(byte[] png) {
    try {
      if (this.directory == null) {
        this.directory = Files.createTempDirectory("lwjwae-notifications");
      }
      Path file = this.directory.resolve("image-" + IMAGE_IDS.incrementAndGet() + ".png");
      Files.write(file, png);
      return file;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Deletes a file, or an empty directory. A failure leaves a file in the temporary directory. */
  static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      ThrowableUtil.report(e);
    }
  }
}
