package dev.ivchenko.lwjwae.download;

import dev.ivchenko.lwjwae.event.DownloadEvent;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(20)
class WindowDownloadsTest {
  private static final URI URL = URI.create("https://example.com/files/report.pdf");
  private static final DownloadRequest REQUEST =
      new DownloadRequest(URL, "report.pdf", "application/pdf", 1000);

  private final List<String> page = new CopyOnWriteArrayList<>();

  private WindowDownloads downloads(Optional<Path> asked) {
    return new WindowDownloads(null, this.page::add, _ -> CompletableFuture.completedFuture(asked));
  }

  @Test
  void theHandlerDecidesAndSavingMakesTheFolder(@TempDir Path directory) throws Exception {
    WindowDownloads downloads = this.downloads(Optional.empty());
    Path target = directory.resolve("nested/report.pdf");
    downloads.handler(_ -> DownloadDecision.saveTo(target));

    Assertions.assertEquals(Optional.of(target), downloads.request(REQUEST).get());
    Assertions.assertTrue(Files.isDirectory(target.getParent()));
  }

  @Test
  void askingShowsTheDialogWithTheNameAlone(@TempDir Path directory) throws Exception {
    Path picked = directory.resolve("picked.pdf");
    List<String> proposed = new CopyOnWriteArrayList<>();
    WindowDownloads downloads =
        new WindowDownloads(
            null,
            this.page::add,
            name -> {
              proposed.add(name);
              return CompletableFuture.completedFuture(Optional.of(picked));
            });
    downloads.handler(_ -> DownloadDecision.ask());

    DownloadRequest request = new DownloadRequest(URL, "../../etc/report.pdf", "", -1);
    Assertions.assertEquals(Optional.of(picked), downloads.request(request).get());
    Assertions.assertEquals(List.of("report.pdf"), proposed, "no path of the server's");
  }

  @Test
  void denialThrowingHandlerAndNoAnswerDownloadNothing() throws Exception {
    WindowDownloads downloads = this.downloads(Optional.empty());
    downloads.handler(_ -> DownloadDecision.deny());
    Assertions.assertEquals(Optional.empty(), downloads.request(REQUEST).get());
    downloads.handler(_ -> null);
    Assertions.assertEquals(Optional.empty(), downloads.request(REQUEST).get());
    downloads.handler(
        _ -> {
          throw new IllegalStateException("thrown on purpose by the test");
        });
    Assertions.assertEquals(Optional.empty(), downloads.request(REQUEST).get());
  }

  @Test
  void takenNameGetsNumber(@TempDir Path directory) throws Exception {
    Assertions.assertEquals(
        directory.resolve("report.pdf"), WindowDownloads.unique(directory, "report.pdf"));
    Files.createFile(directory.resolve("report.pdf"));
    Files.createFile(directory.resolve("report (1).pdf"));
    Assertions.assertEquals(
        directory.resolve("report (2).pdf"), WindowDownloads.unique(directory, "report.pdf"));
    Assertions.assertEquals(
        directory.resolve("download"), WindowDownloads.unique(directory, "a/b/.."));
  }

  @Test
  void stepsArriveInOrderAndProgressIsThinnedOut(@TempDir Path directory) throws Exception {
    WindowDownloads downloads = this.downloads(Optional.empty());
    BlockingQueue<DownloadEvent> events = new LinkedBlockingQueue<>();
    downloads.listen(events::add);
    Path path = directory.resolve("report.pdf");
    Files.write(path, new byte[1000]);

    long id = downloads.started(URL, path, 1000, () -> {});
    for (int received = 1; received <= 1000; received++) {
      downloads.progressed(id, received, 1000);
    }
    downloads.ended(id, DownloadState.COMPLETED, "");
    downloads.ended(id, DownloadState.FAILED, "a second end is ignored");

    List<DownloadEvent> heard = new CopyOnWriteArrayList<>();
    while (heard.isEmpty() || !heard.getLast().state().isFinal()) {
      heard.add(events.poll(5, TimeUnit.SECONDS));
    }
    Assertions.assertEquals(DownloadState.STARTED, heard.getFirst().state());
    Assertions.assertEquals(DownloadState.COMPLETED, heard.getLast().state());
    Assertions.assertEquals(1000, heard.getLast().receivedBytes());
    Assertions.assertTrue(heard.size() <= 4, "a thousand reports in a moment are a few: " + heard);
    Assertions.assertNull(events.poll(200, TimeUnit.MILLISECONDS), "nothing after the end");
    Assertions.assertTrue(
        this.page.getFirst().contains("\"state\":\"started\""), this.page.toString());
    Assertions.assertTrue(this.page.getLast().contains("\"state\":\"completed\""));
  }

  @Test
  void cancelingRunsOnTheUiThreadOnlyWhileTheDownloadGoes(@TempDir Path directory) {
    WindowDownloads downloads = this.downloads(Optional.empty());
    AtomicInteger canceled = new AtomicInteger();
    long id = downloads.started(URL, directory.resolve("x"), -1, canceled::incrementAndGet);

    Assertions.assertTrue(downloads.cancel(id, Runnable::run));
    Assertions.assertEquals(1, canceled.get());
    downloads.ended(id, DownloadState.CANCELED, "");
    Assertions.assertFalse(downloads.cancel(id, Runnable::run));
    Assertions.assertFalse(downloads.cancel(id + 1, Runnable::run));
    Assertions.assertEquals(1, canceled.get());
  }
}
