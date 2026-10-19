package dev.ivchenko.lwjwae.download;

/** What a window does with a download, see {@link DownloadDecision}. */
public enum DownloadAction {
  /** The file goes to the path of the decision. */
  SAVE,

  /** The dialog of the platform that saves a file asks the user where it goes. */
  ASK,

  /** Nothing is downloaded, and the page stays as it is. */
  DENY
}
