package dev.ivchenko.lwjwae.event;

import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.download.DownloadState;
import java.net.URI;
import java.nio.file.Path;

/**
 * A step of a download of a page, see {@link Window#onDownload}. Every download starts with {@link
 * DownloadState#STARTED} and ends with one of {@link DownloadState#COMPLETED}, {@link
 * DownloadState#FAILED}, and {@link DownloadState#CANCELED}, with any number of {@link
 * DownloadState#PROGRESSED} between them, at most about ten a second.
 *
 * @param window The window whose page started the download.
 * @param id The download, the same in each of its events, for {@link Window#cancelDownload}.
 * @param url Where the file comes from.
 * @param path Where the file goes.
 * @param state What happened.
 * @param receivedBytes How much of the file arrived.
 * @param totalBytes The size of the file, or -1 where the server didn't send it.
 * @param failure What went wrong, for {@link DownloadState#FAILED}, and an empty string otherwise.
 */
public record DownloadEvent(
    Window window,
    long id,
    URI url,
    Path path,
    DownloadState state,
    long receivedBytes,
    long totalBytes,
    String failure) {
  public DownloadEvent {
    if (failure == null) {
      failure = "";
    }
  }
}
