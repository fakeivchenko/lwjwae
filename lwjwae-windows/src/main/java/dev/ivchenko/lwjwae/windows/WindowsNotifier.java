package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.AbstractNotification;
import dev.ivchenko.lwjwae.notification.Notification;
import dev.ivchenko.lwjwae.notification.NotificationAction;
import dev.ivchenko.lwjwae.notification.NotificationHandle;
import dev.ivchenko.lwjwae.ui.UiDispatcher;
import dev.ivchenko.lwjwae.util.TemporaryImages;
import dev.ivchenko.lwjwae.windows.binding.Advapi32;
import dev.ivchenko.lwjwae.windows.binding.Com;
import dev.ivchenko.lwjwae.windows.binding.ComCallback;
import dev.ivchenko.lwjwae.windows.binding.ComEvent;
import dev.ivchenko.lwjwae.windows.binding.Toasts;
import dev.ivchenko.lwjwae.windows.exception.ComCallFailedException;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The notifications of one application, as toasts: the notifications of Windows 10 and 11, which
 * pop up in the corner of the screen and stay in the notification center afterwards.
 *
 * <p>Windows files toasts under an application user model ID (AUMID), and shows the name and the
 * icon registered for it. A packaged application has one from its manifest; a plain executable
 * registers one under {@code HKCU\Software\Classes\AppUserModelId}, which is what the notifier does
 * on its first use. The ID is {@code lwjwae.}, the letters and digits of the name, and a hash of
 * the whole name, so that two applications keep their notifications apart even when their names
 * differ only in characters that an ID can't hold, such as Cyrillic ones. Without a name, the name
 * is that of the main class or JAR for a JVM, whose executable is {@code java} for every
 * application, and that of the executable otherwise. The key stays when the application exits, as
 * it must: the notification center still lists the toasts of the application after it's gone, and
 * it needs the name to label them.
 *
 * <p>The content of a toast is XML in the {@code ToastGeneric} template: two text lines, an image
 * as a {@code file:} URI in {@code appLogoOverride} placement, and one {@code action} per button.
 * The toast itself carries the argument {@code default} for a click on its body, and each button
 * {@code action-N}, as on Linux. The events of a toast fire on a thread of the pool, so the
 * handlers are agile COM objects, and they touch nothing that belongs to the UI thread.
 */
final class WindowsNotifier {
  private static final String REGISTRY_KEY = "Software\\Classes\\AppUserModelId\\";

  private final UiDispatcher dispatcher;
  private final String applicationId;

  private final TemporaryImages images = new TemporaryImages("lwjwae-notifications");

  private volatile MemorySegment notifier;

  /**
   * Registers the application ID and creates the toast notifier.
   *
   * @param applicationName The name that Windows shows on the toasts, or {@code null} for the name
   *     of the main class or the executable.
   */
  WindowsNotifier(UiDispatcher dispatcher, String applicationName) {
    this.dispatcher = dispatcher;
    String displayName = applicationName == null ? WindowsNotifier.defaultName() : applicationName;
    this.applicationId = WindowsNotifier.idFor(displayName);
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

  /**
   * Shows {@code notification} as a toast.
   *
   * @throws UnsupportedOperationException If Windows doesn't let the application show toasts.
   */
  WindowsNotification show(Notification notification, Consumer<NotificationHandle> closed) {
    Path image = notification.icon() == null ? null : this.images.write(notification.icon());
    try {
      return this.dispatcher.call(() -> this.showNow(notification, image, closed));
    } catch (RuntimeException e) {
      TemporaryImages.delete(image);
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
    MemorySegment toast = Toasts.notification(WindowsNotifier.xml(notification, image));
    try {
      WindowsNotification handle =
          new WindowsNotification(this, toast, notification, image, closed);
      WindowsNotifier.subscribe(
          toast,
          Toasts.IID_ACTIVATED_HANDLER,
          Toasts::onActivated,
          (_, arguments) -> handle.activated(Toasts.activatedArguments(arguments)));
      WindowsNotifier.subscribe(
          toast,
          Toasts.IID_DISMISSED_HANDLER,
          Toasts::onDismissed,
          (_, arguments) -> handle.dismissed(Toasts.dismissalReason(arguments)));
      WindowsNotifier.subscribe(
          toast, Toasts.IID_FAILED_HANDLER, Toasts::onFailed, (_, _) -> handle.failed());
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
    this.images.deleteAll();
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
    // ComCallback reports whatever the handler throws, so the handler needs no catch of its own.
    MemorySegment handler = ComCallback.agileEvent(iid, event);
    try {
      subscription.accept(toast, handler);
    } finally {
      Com.release(handler);
    }
  }

  /** The toast XML of {@code notification}. */
  static String xml(Notification notification, Path image) {
    StringBuilder xml = new StringBuilder();
    xml.append("<toast launch=\"").append(AbstractNotification.DEFAULT_ACTION).append("\">");
    xml.append("<visual><binding template=\"ToastGeneric\">");
    xml.append("<text>").append(WindowsNotifier.escape(notification.title())).append("</text>");
    if (notification.body() != null) {
      xml.append("<text>").append(WindowsNotifier.escape(notification.body())).append("</text>");
    }
    if (image != null) {
      xml.append("<image placement=\"appLogoOverride\" src=\"")
          .append(WindowsNotifier.escape(image.toUri().toString()))
          .append("\"/>");
    }
    xml.append("</binding></visual>");
    List<NotificationAction> actions = notification.actions();
    if (!actions.isEmpty()) {
      xml.append("<actions>");
      for (int index = 0; index < actions.size(); index++) {
        xml.append("<action content=\"")
            .append(WindowsNotifier.escape(actions.get(index).label()))
            .append("\" arguments=\"")
            .append(AbstractNotification.buttonAction(index))
            .append("\"/>");
      }
      xml.append("</actions>");
    }
    return xml.append("</toast>").toString();
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  /**
   * The AppUserModelID for {@code displayName}: {@code lwjwae.}, its ASCII letters and digits, and
   * eight hex digits of its SHA-256, which tell apart names that differ only elsewhere.
   */
  static String idFor(String displayName) {
    String readable = displayName.replaceAll("[^A-Za-z0-9]+", ".").replaceAll("^\\.|\\.$", "");
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(displayName.getBytes(StandardCharsets.UTF_8));
      String hash = HexFormat.of().formatHex(digest, 0, 4);
      return "lwjwae." + (readable.isEmpty() ? "" : readable + ".") + hash;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every Java platform has SHA-256", e);
    }
  }

  /**
   * The name of an application that gave none: the main class or the JAR that the JVM runs, or the
   * executable of a native image.
   */
  private static String defaultName() {
    String command = System.getProperty("sun.java.command");
    if (command != null && !command.isBlank()) {
      String main = command.strip().split("\\s+")[0];
      String file = Path.of(main).getFileName().toString();
      return file.endsWith(".jar") ? file.substring(0, file.length() - 4) : file;
    }
    String name =
        ProcessHandle.current()
            .info()
            .command()
            .map(executable -> Path.of(executable).getFileName().toString())
            .orElse("lwjwae");
    return name.toLowerCase(Locale.ROOT).endsWith(".exe")
        ? name.substring(0, name.length() - 4)
        : name;
  }
}
