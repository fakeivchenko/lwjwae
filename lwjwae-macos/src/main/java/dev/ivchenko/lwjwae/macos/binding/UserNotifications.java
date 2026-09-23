package dev.ivchenko.lwjwae.macos.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The UserNotifications framework: {@code UNUserNotificationCenter} and what it takes.
 *
 * <p>The center belongs to an application bundle. Asked for from a process that isn't one, such as
 * the {@code java} launcher or a bare executable, {@code currentNotificationCenter} raises an
 * Objective-C exception, which no Java code can catch; {@link #isBundledApplication()} is the check
 * that keeps a caller from asking.
 */
@UtilityClass
public class UserNotifications {
  static {
    // Loaded for the classes it registers; nothing is looked up by symbol.
    SymbolLookup _ =
        NativeLibraries.load(
            "/System/Library/Frameworks/UserNotifications.framework/UserNotifications");
  }

  /** {@code UNNotificationDefaultActionIdentifier}: a click on the notification itself. */
  public final String DEFAULT_ACTION = "com.apple.UNNotificationDefaultActionIdentifier";

  /** {@code UNNotificationDismissActionIdentifier}: the user dismissed the notification. */
  public final String DISMISS_ACTION = "com.apple.UNNotificationDismissActionIdentifier";

  /** {@code UNAuthorizationOptionSound | UNAuthorizationOptionAlert}. */
  public final long AUTHORIZATION_SOUND_AND_ALERT = (1 << 1) | (1 << 2);

  /**
   * {@code UNNotificationPresentationOptionList | UNNotificationPresentationOptionBanner}: how a
   * notification shows while its application is in front, which by default it doesn't.
   */
  public final long PRESENT_AS_BANNER = (1 << 3) | (1 << 4);

  /**
   * {@code UNNotificationCategoryOptionCustomDismissAction}: report a dismissal to the delegate.
   */
  private final long CATEGORY_CUSTOM_DISMISS_ACTION = 1;

  private final long ACTION_OPTIONS_NONE = 0;

  /** Whether the process runs from an {@code .app} bundle, the one kind that has a center. */
  public boolean isBundledApplication() {
    MemorySegment bundle = ObjC.send(ObjC.cls("NSBundle"), "mainBundle");
    String path = Foundation.string(ObjC.send(bundle, "bundlePath"));
    String identifier = Foundation.string(ObjC.send(bundle, "bundleIdentifier"));
    return identifier != null && path != null && path.endsWith(".app");
  }

  /** {@code [NSBundle mainBundle].bundleIdentifier}, or {@code null}. */
  public String bundleIdentifier() {
    return Foundation.string(
        ObjC.send(ObjC.send(ObjC.cls("NSBundle"), "mainBundle"), "bundleIdentifier"));
  }

  /** {@code +[UNUserNotificationCenter currentNotificationCenter]}. Only in a bundle. */
  public MemorySegment center() {
    return ObjC.send(ObjC.cls("UNUserNotificationCenter"), "currentNotificationCenter");
  }

  /** {@code -[UNUserNotificationCenter setDelegate:]}. The center holds the delegate weakly. */
  public void setDelegate(MemorySegment center, MemorySegment delegate) {
    ObjC.sendVoid(center, "setDelegate:", delegate);
  }

  /** {@code requestAuthorizationWithOptions:completionHandler:} for alerts and sounds. */
  public void requestAuthorization(MemorySegment center, MemorySegment completion) {
    ObjC.sendVoid(
        center,
        "requestAuthorizationWithOptions:completionHandler:",
        AUTHORIZATION_SOUND_AND_ALERT,
        completion);
  }

  /** An autoreleased {@code UNNotificationAction} that brings nothing to the front. */
  public MemorySegment action(String identifier, String title) {
    return ObjC.send(
        ObjC.cls("UNNotificationAction"),
        "actionWithIdentifier:title:options:",
        Foundation.string(identifier),
        Foundation.string(title),
        ACTION_OPTIONS_NONE);
  }

  /** An autoreleased {@code UNNotificationCategory} whose dismissal reaches the delegate. */
  public MemorySegment category(String identifier, List<MemorySegment> actions) {
    return ObjC.send(
        ObjC.cls("UNNotificationCategory"),
        "categoryWithIdentifier:actions:intentIdentifiers:options:",
        Foundation.string(identifier),
        Foundation.array(actions),
        Foundation.array(List.of()),
        CATEGORY_CUSTOM_DISMISS_ACTION);
  }

  /** {@code -[UNUserNotificationCenter setNotificationCategories:]}: every category at once. */
  public void setCategories(MemorySegment center, List<MemorySegment> categories) {
    MemorySegment set = ObjC.send(ObjC.cls("NSSet"), "setWithArray:", Foundation.array(categories));
    ObjC.sendVoid(center, "setNotificationCategories:", set);
  }

  /**
   * An owned {@code UNMutableNotificationContent}.
   *
   * @param image A PNG file to attach, or {@code null}. The system moves the file into its store.
   */
  public MemorySegment content(String title, String body, String category, String image) {
    MemorySegment content =
        ObjC.send(ObjC.send(ObjC.cls("UNMutableNotificationContent"), "alloc"), "init");
    ObjC.sendVoid(content, "setTitle:", Foundation.string(title));
    if (body != null) {
      ObjC.sendVoid(content, "setBody:", Foundation.string(body));
    }
    ObjC.sendVoid(content, "setCategoryIdentifier:", Foundation.string(category));
    if (image != null) {
      MemorySegment url =
          ObjC.send(ObjC.cls("NSURL"), "fileURLWithPath:", Foundation.string(image));
      MemorySegment attachment =
          ObjC.sendIgnoringError(
              ObjC.cls("UNNotificationAttachment"),
              "attachmentWithIdentifier:URL:options:error:",
              Foundation.string("image"),
              url,
              MemorySegment.NULL);
      if (!ObjC.isNull(attachment)) {
        ObjC.sendVoid(content, "setAttachments:", Foundation.array(List.of(attachment)));
      }
    }
    return content;
  }

  /**
   * Hands a request to the center: {@code addNotificationRequest:withCompletionHandler:}, with a
   * request that shows at once.
   */
  public void add(
      MemorySegment center, String identifier, MemorySegment content, MemorySegment completion) {
    MemorySegment request =
        ObjC.send(
            ObjC.cls("UNNotificationRequest"),
            "requestWithIdentifier:content:trigger:",
            Foundation.string(identifier),
            content,
            MemorySegment.NULL);
    ObjC.sendVoid(center, "addNotificationRequest:withCompletionHandler:", request, completion);
  }

  /** Takes a notification off the screen and out of Notification Center, shown or pending. */
  public void remove(MemorySegment center, String identifier) {
    MemorySegment identifiers = Foundation.array(List.of(Foundation.string(identifier)));
    ObjC.sendVoid(center, "removeDeliveredNotificationsWithIdentifiers:", identifiers);
    ObjC.sendVoid(center, "removePendingNotificationRequestsWithIdentifiers:", identifiers);
  }

  /** {@code response.notification.request.identifier}. */
  public String responseNotification(MemorySegment response) {
    return Foundation.string(
        ObjC.send(ObjC.send(ObjC.send(response, "notification"), "request"), "identifier"));
  }

  /** {@code response.actionIdentifier}. */
  public String responseAction(MemorySegment response) {
    return Foundation.string(ObjC.send(response, "actionIdentifier"));
  }
}
