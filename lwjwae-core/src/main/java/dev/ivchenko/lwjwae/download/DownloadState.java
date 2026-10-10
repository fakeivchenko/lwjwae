package dev.ivchenko.lwjwae.download;

import java.util.Locale;

/** Where a download is, as a {@link dev.ivchenko.lwjwae.event.DownloadEvent} reports it. */
public enum DownloadState {
  /** The file has a path, and the first bytes are on their way. */
  STARTED,

  /** More of the file arrived. */
  PROGRESSED,

  /** The whole file is at its path. */
  COMPLETED,

  /** The download stopped on an error, such as a lost connection or a full disk. */
  FAILED,

  /**
   * The download was canceled, by {@link dev.ivchenko.lwjwae.Window#cancelDownload} or the page.
   */
  CANCELED;

  /** The name that a page sees, such as {@code started}. */
  public String pageName() {
    return this.name().toLowerCase(Locale.ROOT);
  }

  /** Whether the download is over: completed, failed, or canceled. */
  public boolean isFinal() {
    return this == COMPLETED || this == FAILED || this == CANCELED;
  }
}
