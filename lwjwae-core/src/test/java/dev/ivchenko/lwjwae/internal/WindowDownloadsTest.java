package dev.ivchenko.lwjwae.internal;

import dev.ivchenko.lwjwae.download.DownloadDecision;
import dev.ivchenko.lwjwae.download.DownloadRequest;
import dev.ivchenko.lwjwae.download.DownloadState;
import dev.ivchenko.lwjwae.event.DownloadEvent;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Stream;
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
    return new WindowDownloads(
        null, Runnable::run, this.page::add, _ -> CompletableFuture.completedFuture(asked));
  }

  /** Downloads that go to {@code folder} without a handler, and that never ask. */
  private WindowDownloads downloads(Path folder) {
    return new WindowDownloads(
        null,
        Runnable::run,
        this.page::add,
        _ -> CompletableFuture.completedFuture(Optional.empty()),
        () -> folder);
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
            Runnable::run,
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
    Assertions.assertTrue(Files.exists(directory.resolve("report.pdf")), "the name is claimed");
    Files.createFile(directory.resolve("report (1).pdf"));
    Assertions.assertEquals(
        directory.resolve("report (2).pdf"), WindowDownloads.unique(directory, "report.pdf"));
    Assertions.assertEquals(
        directory.resolve("download"), WindowDownloads.unique(directory, "a/b/.."));
  }

  @Test
  void nameOfTheServerBecomesOneThatEveryFileSystemTakes() {
    Assertions.assertEquals("report.pdf", WindowDownloads.fileName("../../etc/report.pdf"));
    Assertions.assertEquals("a_b_c_.txt", WindowDownloads.fileName("a<b>c?.txt"));
    Assertions.assertEquals("notes", WindowDownloads.fileName("notes. . ."));
    Assertions.assertEquals("_con.txt", WindowDownloads.fileName("con.txt"));
    Assertions.assertEquals("download", WindowDownloads.fileName(" .. "));
    // APFS refuses a code point that Unicode never assigned, and half of a surrogate pair.
    Assertions.assertEquals("a_b_.txt", WindowDownloads.fileName("a\u0378b\ud800.txt"));

    String longName = WindowDownloads.fileName("é".repeat(300) + ".pdf");
    Assertions.assertTrue(longName.endsWith(".pdf"), "the extension stays");
    Assertions.assertTrue(longName.getBytes(StandardCharsets.UTF_8).length <= 200, longName);
  }

  @Test
  void claimedNameStaysTakenUntilItsDownloadEnds(@TempDir Path directory) throws Exception {
    Path first = WindowDownloads.unique(directory, "notes.txt");
    // As WebKit on macOS needs: the backend removes the file before the engine writes it.
    Files.delete(first);

    Assertions.assertEquals(
        directory.resolve("notes (1).txt"), WindowDownloads.unique(directory, "notes.txt"));
    WindowDownloads.release(first, false);
    Assertions.assertEquals(first, WindowDownloads.unique(directory, "notes.txt"));
  }

  @Test
  void withoutHandlerTheFileGoesToTheDownloadsFolder(@TempDir Path directory) throws Exception {
    WindowDownloads downloads = this.downloads(directory);

    Path path = downloads.request(REQUEST).get().orElseThrow();
    Assertions.assertEquals(directory.resolve("report.pdf"), path);
    long id = downloads.started(URL, path, 1000, () -> {});
    Files.write(path, new byte[1000]);
    downloads.ended(id, DownloadState.COMPLETED, "");

    Assertions.assertEquals(1000, Files.size(path), "a completed download keeps its file");
  }

  @Test
  void downloadThatDoesNotCompleteTakesItsClaimedFileWithIt(@TempDir Path directory)
      throws Exception {
    WindowDownloads downloads = this.downloads(directory);

    Path canceled = downloads.request(REQUEST).get().orElseThrow();
    Path failed = downloads.request(REQUEST).get().orElseThrow();
    Assertions.assertNotEquals(canceled, failed);
    downloads.ended(downloads.started(URL, canceled, 1000, () -> {}), DownloadState.CANCELED, "");
    downloads.ended(downloads.started(URL, failed, 1000, () -> {}), DownloadState.FAILED, "lost");

    Assertions.assertFalse(Files.exists(canceled));
    Assertions.assertFalse(Files.exists(failed));
  }

  @Test
  void closingRemovesTheClaimedFileOfAnUnstartedDownload(@TempDir Path directory) throws Exception {
    WindowDownloads downloads = this.downloads(directory);
    Path decided = downloads.request(REQUEST).get().orElseThrow();

    downloads.shutdown();

    Assertions.assertFalse(Files.exists(decided));
    Assertions.assertEquals(Optional.empty(), downloads.request(REQUEST).get());
    try (Stream<Path> left = Files.list(directory)) {
      Assertions.assertEquals(List.of(), left.toList(), "a decision after the close claims none");
    }
  }

  @Test
  void fileThatTheHandlerNamesStaysWhenTheDownloadFails(@TempDir Path directory) throws Exception {
    WindowDownloads downloads = this.downloads(directory);
    Path own = Files.writeString(directory.resolve("own.pdf"), "the application's");
    downloads.handler(_ -> DownloadDecision.saveTo(own));

    Path path = downloads.request(REQUEST).get().orElseThrow();
    downloads.ended(downloads.started(URL, path, 1000, () -> {}), DownloadState.FAILED, "lost");

    Assertions.assertEquals("the application's", Files.readString(own));
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

    Assertions.assertTrue(downloads.cancel(id));
    Assertions.assertEquals(1, canceled.get());
    downloads.ended(id, DownloadState.CANCELED, "");
    Assertions.assertFalse(downloads.cancel(id));
    Assertions.assertFalse(downloads.cancel(id + 1));
    Assertions.assertEquals(1, canceled.get());
  }
}
