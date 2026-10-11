package dev.ivchenko.lwjwae.download;

import dev.ivchenko.lwjwae.event.DownloadEvent;
import dev.ivchenko.lwjwae.event.EventSubscription;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The downloads of the page of one window: where they go, how they get on, and how they stop.
 * {@link dev.ivchenko.lwjwae.Window#downloads()} hands them out.
 *
 * <p>A download is anything that the engine saves rather than shows: a link with a {@code download}
 * attribute, a file that the server sends as an attachment or that the engine can't show, and a
 * navigation from Java to such a file. The engines report them in their own ways; the window makes
 * them the same on every platform.
 *
 * <p>Every method is safe to call from any thread, and stays harmless once the window is closed.
 */
public interface Downloads {
  /**
   * Decides where a download of the page goes. By default, the file goes to the downloads folder of
   * the user, under the name that the server suggests, with a number added where a file of that
   * name is there.
   *
   * <pre>{@code
   * window.downloads().handler(request ->
   *     request.suggestedFileName().endsWith(".pdf")
   *         ? DownloadDecision.saveTo(reports.resolve(request.suggestedFileName()))
   *         : DownloadDecision.ask());
   * }</pre>
   *
   * <p>Platforms:
   *
   * <ul>
   *   <li>Windows: Through the {@code DownloadStarting} event of WebView2, which needs the runtime
   *       1.0.902 or later. The download bubble of Edge never shows.
   *   <li>macOS: Through {@code WKDownload}, which needs macOS 11.3 or later.
   *   <li>Linux, GTK 3: Through the {@code decide-destination} signal of {@code WebKitDownload}.
   *   <li>Linux, GTK 4: As on GTK 3.
   * </ul>
   *
   * @param handler Receives each request on a virtual thread of its own, so it may block, for
   *     example on a dialog, while the engine waits. A handler that throws or answers {@code null}
   *     downloads nothing. {@code null} brings the default back.
   */
  void handler(Function<DownloadRequest, DownloadDecision> handler);

  /**
   * Calls {@code listener} as each download of the page starts, gets on, and ends. A page hears the
   * same through {@code lwjwae.downloads.listen}.
   *
   * @param listener Receives the events on a virtual thread, one after the other.
   * @return The subscription, to stop listening.
   */
  EventSubscription listen(Consumer<DownloadEvent> listener);

  /**
   * Cancels the download {@code id} of a {@link DownloadEvent}, and returns without waiting for the
   * engine; a {@link DownloadState#CANCELED} event follows. A page cancels with {@code
   * lwjwae.downloads.cancel(id)}.
   *
   * @return Whether the download was still going.
   */
  boolean cancel(long id);
}
