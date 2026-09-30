package dev.ivchenko.lwjwae.cookie;

/** When a browser sends a cookie with a request that another site started. */
public enum SameSite {
  /** With every request, which a browser allows only for a secure cookie. */
  NONE,

  /** With a request of this site, and with a navigation to it from another. */
  LAX,

  /** With a request of this site only. */
  STRICT
}
