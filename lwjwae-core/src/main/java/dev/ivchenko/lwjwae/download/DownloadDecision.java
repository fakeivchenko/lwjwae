package dev.ivchenko.lwjwae.download;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where a download goes, which {@link dev.ivchenko.lwjwae.Window#downloadHandler} answers with.
 *
 * @param action Whether the file is saved, the user is asked, or nothing is downloaded.
 * @param path The file to write for {@link DownloadAction#SAVE}, as an absolute path, and {@code
 *     null} otherwise. A file that is there is replaced.
 */
public record DownloadDecision(DownloadAction action, Path path) {
  public DownloadDecision {
    Objects.requireNonNull(action, "action");
    if (action == DownloadAction.SAVE) {
      Objects.requireNonNull(path, "path");
      path = path.toAbsolutePath().normalize();
    } else {
      path = null;
    }
  }

  /** Saves the file at {@code path}. */
  public static DownloadDecision saveTo(Path path) {
    return new DownloadDecision(DownloadAction.SAVE, path);
  }

  /** Asks the user where the file goes, with the suggested name proposed. */
  public static DownloadDecision ask() {
    return new DownloadDecision(DownloadAction.ASK, null);
  }

  /** Downloads nothing. */
  public static DownloadDecision deny() {
    return new DownloadDecision(DownloadAction.DENY, null);
  }
}
