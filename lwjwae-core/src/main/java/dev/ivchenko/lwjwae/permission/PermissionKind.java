package dev.ivchenko.lwjwae.permission;

/** What a page asks the user for, see {@link dev.ivchenko.lwjwae.Window#permissionHandler}. */
public enum PermissionKind {
  /** A video input, with {@code getUserMedia({ video: true })}. */
  CAMERA,

  /** An audio input, with {@code getUserMedia({ audio: true })}. */
  MICROPHONE,

  /** The position of the device, with {@code navigator.geolocation}. */
  GEOLOCATION,

  /** Notifications, with {@code Notification.requestPermission()}. */
  NOTIFICATIONS
}
