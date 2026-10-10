package dev.ivchenko.lwjwae.download;

import java.net.URI;
import java.nio.file.Path;

/**
 * A download that hasn't ended, as {@link WindowDownloads} keeps it. The counts are written by the
 * UI thread that the engine reports on and read for the events it sends, so they are volatile.
 */
final class ActiveDownload {
  final long id;
  final URI url;
  final Path path;
  final Runnable cancel;
  volatile long receivedBytes;
  volatile long totalBytes;
  volatile long reportedAt;

  ActiveDownload(long id, URI url, Path path, Runnable cancel, long totalBytes, long reportedAt) {
    this.id = id;
    this.url = url;
    this.path = path;
    this.cancel = cancel;
    this.totalBytes = totalBytes;
    this.reportedAt = reportedAt;
  }
}
