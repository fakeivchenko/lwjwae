package dev.ivchenko.lwjwae.permission;

/** What a window does with a request of a page for a permission. */
public enum PermissionDecision {
  /** The page gets the permission. */
  GRANT,

  /** The page gets no permission, and the request fails on the page as if the user had said no. */
  DENY
}
