package dev.ivchenko.lwjwae.dialog;

/** How much a message matters, which picks the icon of the dialog where the platform shows one. */
public enum MessageLevel {
  /** Something the user may want to know. */
  INFO,

  /** Something that may go wrong. */
  WARNING,

  /** Something that went wrong. */
  ERROR,

  /** A question for the user. */
  QUESTION
}
