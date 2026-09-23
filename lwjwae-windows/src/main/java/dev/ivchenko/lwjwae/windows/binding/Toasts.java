package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.foreign.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import lombok.experimental.UtilityClass;

/**
 * Bindings to toast notifications, {@code Windows.UI.Notifications}, addressed by vtable slot as
 * {@link WebView2} is.
 *
 * <p>The IIDs and slots are those of {@code windows.ui.notifications.h} and {@code
 * windows.data.xml.dom.h} in the Windows SDK. The IIDs of the event handlers are those of the
 * parameterized {@code TypedEventHandler} instances that the SDK headers declare; the runtime
 * derives them from the type signature, so they are the same on every Windows.
 */
@UtilityClass
public class Toasts {
  private final String MANAGER_CLASS = "Windows.UI.Notifications.ToastNotificationManager";
  private final String NOTIFICATION_CLASS = "Windows.UI.Notifications.ToastNotification";
  private final String XML_DOCUMENT_CLASS = "Windows.Data.Xml.Dom.XmlDocument";

  private final MemorySegment IID_MANAGER_STATICS =
      Com.guid("50ac103f-d235-4598-bbef-98fe4d1a3ad4");
  private final MemorySegment IID_NOTIFICATION_FACTORY =
      Com.guid("04124b20-82c6-4229-b109-fd9ed4662b53");
  private final MemorySegment IID_XML_DOCUMENT = Com.guid("f7f3a506-1e87-42d6-bcfb-b8c809fa5494");
  private final MemorySegment IID_XML_DOCUMENT_IO =
      Com.guid("6cd0e74e-ee65-4489-9ebf-ca43e87ba637");
  private final MemorySegment IID_ACTIVATED_ARGUMENTS =
      Com.guid("e3bf92f3-c197-436f-8265-0625824f8dac");
  private final MemorySegment IID_DISMISSED_ARGUMENTS =
      Com.guid("3f89d935-d9cb-4538-a0f0-ffe7659938f8");

  /** {@code TypedEventHandler<ToastNotification, IInspectable>}: {@code Activated}. */
  public final MemorySegment IID_ACTIVATED_HANDLER =
      Com.guid("ab54de2d-97d9-5528-b6ad-105afe156530");

  /** {@code TypedEventHandler<ToastNotification, ToastDismissedEventArgs>}: {@code Dismissed}. */
  public final MemorySegment IID_DISMISSED_HANDLER =
      Com.guid("61c2402f-0ed0-5a18-ab69-59f4aa99a368");

  /** {@code TypedEventHandler<ToastNotification, ToastFailedEventArgs>}: {@code Failed}. */
  public final MemorySegment IID_FAILED_HANDLER = Com.guid("95e3e803-c969-5e3a-9753-ea2ad22a9a33");

  /** {@code NotificationSetting_Enabled}. */
  public final int SETTING_ENABLED = 0;

  /** No setting for the application yet: Windows decides when the toast is shown. */
  public final int SETTING_UNKNOWN = -1;

  /** {@code HRESULT_FROM_WIN32(ERROR_NOT_FOUND)}. */
  private final int ERROR_NOT_FOUND = 0x80070490;

  /** {@code ToastDismissalReason_UserCanceled}. */
  public final int DISMISSED_BY_USER = 0;

  /** {@code ToastDismissalReason_TimedOut}. */
  public final int DISMISSED_BY_TIMEOUT = 2;

  // IToastNotificationManagerStatics
  private final int MANAGER_CREATE_NOTIFIER_WITH_ID = WinRt.FIRST_SLOT + 1;
  // IToastNotifier
  private final int NOTIFIER_SHOW = WinRt.FIRST_SLOT;
  private final int NOTIFIER_HIDE = WinRt.FIRST_SLOT + 1;
  private final int NOTIFIER_GET_SETTING = WinRt.FIRST_SLOT + 2;
  // IToastNotificationFactory
  private final int FACTORY_CREATE_NOTIFICATION = WinRt.FIRST_SLOT;
  // IXmlDocumentIO
  private final int XML_LOAD_XML = WinRt.FIRST_SLOT;
  // IToastNotification
  private final int NOTIFICATION_ADD_DISMISSED = WinRt.FIRST_SLOT + 3;
  private final int NOTIFICATION_ADD_ACTIVATED = WinRt.FIRST_SLOT + 5;
  private final int NOTIFICATION_ADD_FAILED = WinRt.FIRST_SLOT + 7;
  // IToastActivatedEventArgs
  private final int ACTIVATED_GET_ARGUMENTS = WinRt.FIRST_SLOT;
  // IToastDismissedEventArgs
  private final int DISMISSED_GET_REASON = WinRt.FIRST_SLOT;

  /**
   * {@code ToastNotificationManager.CreateToastNotifier(id)}: the notifier of the application with
   * the AppUserModelID {@code applicationId}, which the caller releases.
   */
  public MemorySegment notifier(String applicationId) {
    MemorySegment statics = WinRt.activationFactory(MANAGER_CLASS, IID_MANAGER_STATICS);
    MemorySegment id = WinRt.create(applicationId);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("CreateToastNotifier", Com.call(statics, MANAGER_CREATE_NOTIFIER_WITH_ID, id, out));
      return Com.pointerAt(out);
    } finally {
      WinRt.delete(id);
      Com.release(statics);
    }
  }

  /**
   * {@code IToastNotifier.Setting}: whether the user, a policy, or the system lets the application
   * show toasts. {@link #SETTING_ENABLED} is the only value under which they appear. An application
   * without a Start menu shortcut has no such setting yet, and the call fails with {@code
   * ERROR_NOT_FOUND}; that answers {@link #SETTING_UNKNOWN}, and the toast itself tells, through
   * its {@code Failed} event.
   */
  public int setting(MemorySegment notifier) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_INT);
      int hresult = Com.call(notifier, NOTIFIER_GET_SETTING, out);
      if (hresult == ERROR_NOT_FOUND) {
        return SETTING_UNKNOWN;
      }
      Com.check("get_Setting", hresult);
      return out.get(Signatures.C_INT, 0);
    }
  }

  /** A new {@code ToastNotification} with the content {@code xml}, which the caller releases. */
  public MemorySegment notification(String xml) {
    MemorySegment instance = WinRt.activateInstance(XML_DOCUMENT_CLASS);
    MemorySegment io = null;
    MemorySegment document = null;
    MemorySegment factory = null;
    MemorySegment content = MemorySegment.NULL;
    try (Arena arena = Arena.ofConfined()) {
      content = WinRt.create(xml);
      io = WinRt.query(instance, IID_XML_DOCUMENT_IO);
      Com.check("LoadXml", Com.call(io, XML_LOAD_XML, content));
      document = WinRt.query(instance, IID_XML_DOCUMENT);
      factory = WinRt.activationFactory(NOTIFICATION_CLASS, IID_NOTIFICATION_FACTORY);
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check(
          "CreateToastNotification", Com.call(factory, FACTORY_CREATE_NOTIFICATION, document, out));
      return Com.pointerAt(out);
    } finally {
      WinRt.delete(content);
      Com.release(factory);
      Com.release(document);
      Com.release(io);
      Com.release(instance);
    }
  }

  /** {@code IToastNotifier.Show}. */
  public void show(MemorySegment notifier, MemorySegment notification) {
    Com.check("Show", Com.call(notifier, NOTIFIER_SHOW, notification));
  }

  /**
   * {@code IToastNotifier.Hide}. Answers the {@code HRESULT} rather than throwing: hiding a toast
   * that the user already dismissed fails, and for the caller that's the same outcome.
   */
  public int hide(MemorySegment notifier, MemorySegment notification) {
    return Com.call(notifier, NOTIFIER_HIDE, notification);
  }

  /** {@code add_Activated}. The toast holds its own reference to {@code handler}. */
  public void onActivated(MemorySegment notification, MemorySegment handler) {
    subscribe("add_Activated", notification, NOTIFICATION_ADD_ACTIVATED, handler);
  }

  /** {@code add_Dismissed}. */
  public void onDismissed(MemorySegment notification, MemorySegment handler) {
    subscribe("add_Dismissed", notification, NOTIFICATION_ADD_DISMISSED, handler);
  }

  /** {@code add_Failed}. */
  public void onFailed(MemorySegment notification, MemorySegment handler) {
    subscribe("add_Failed", notification, NOTIFICATION_ADD_FAILED, handler);
  }

  /**
   * {@code IToastActivatedEventArgs.Arguments}: the {@code launch} attribute of the toast for a
   * click on the toast itself, or the {@code arguments} of the button that the user picked.
   */
  public String activatedArguments(MemorySegment arguments) {
    MemorySegment typed = WinRt.query(arguments, IID_ACTIVATED_ARGUMENTS);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("get_Arguments", Com.call(typed, ACTIVATED_GET_ARGUMENTS, out));
      return WinRt.take(Com.pointerAt(out));
    } finally {
      Com.release(typed);
    }
  }

  /** {@code IToastDismissedEventArgs.Reason}. */
  public int dismissalReason(MemorySegment arguments) {
    MemorySegment typed = WinRt.query(arguments, IID_DISMISSED_ARGUMENTS);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_INT);
      Com.check("get_Reason", Com.call(typed, DISMISSED_GET_REASON, out));
      return out.get(Signatures.C_INT, 0);
    } finally {
      Com.release(typed);
    }
  }

  private void subscribe(String call, MemorySegment notification, int slot, MemorySegment handler) {
    try (Arena arena = Arena.ofConfined()) {
      // EventRegistrationToken: the toast is released with its handlers, so no one removes them.
      MemorySegment token = arena.allocate(Layouts.C_LONG_LONG);
      Com.check(call, Com.call(notification, slot, handler, token));
    }
  }
}
