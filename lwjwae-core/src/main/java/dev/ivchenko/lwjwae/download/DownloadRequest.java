package dev.ivchenko.lwjwae.download;

import java.net.URI;
import java.util.Objects;

/**
 * A download that a page started, before it writes anything, see {@link
 * dev.ivchenko.lwjwae.Window#downloadHandler}.
 *
 * @param url Where the file comes from.
 * @param suggestedFileName The name that the server or the page proposes, from the {@code
 *     Content-Disposition} header, the {@code download} attribute of a link, or the URL. Never
 *     empty: {@code download} when nothing proposes one.
 * @param mimeType The media type that the server sent, such as {@code application/pdf}, or an empty
 *     string where it sent none or the engine doesn't tell.
 * @param totalBytes The size of the file, or -1 where the server didn't send it.
 */
public record DownloadRequest(URI url, String suggestedFileName, String mimeType, long totalBytes) {
  public DownloadRequest {
    Objects.requireNonNull(url, "url");
    if (suggestedFileName == null || suggestedFileName.isBlank()) {
      suggestedFileName = "download";
    }
    if (mimeType == null) {
      mimeType = "";
    }
    if (totalBytes < 0) {
      totalBytes = -1;
    }
  }
}
