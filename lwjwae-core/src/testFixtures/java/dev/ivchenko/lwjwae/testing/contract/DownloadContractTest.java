package dev.ivchenko.lwjwae.testing.contract;

import com.sun.net.httpserver.HttpServer;
import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.WindowParameters;
import dev.ivchenko.lwjwae.download.DownloadDecision;
import dev.ivchenko.lwjwae.download.DownloadRequest;
import dev.ivchenko.lwjwae.download.DownloadState;
import dev.ivchenko.lwjwae.event.DownloadEvent;
import dev.ivchenko.lwjwae.testing.Loads;
import dev.ivchenko.lwjwae.testing.Tags;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Downloads of a page, from a server on this machine: where they go, what Java and the page hear of
 * them, and how they end. The server sends each file as an attachment of a type that no engine
 * shows, so every engine downloads it, and Java navigates to it, so no link leaves the application.
 */
@Tag(Tags.DISPLAY)
@Timeout(120)
public abstract class DownloadContractTest extends DisplayContractTest {
  /** The file of {@code /report.bin}, which is sent whole. */
  private static final byte[] REPORT = DownloadContractTest.randomBytes(300 * 1024);

  /** The size that {@code /slow.bin} announces, which it takes far longer than a test to send. */
  private static final int SLOW_SIZE = 64 * 1024 * 1024;

  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    this.server.createContext(
        "/report.bin",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
          exchange
              .getResponseHeaders()
              .add("Content-Disposition", "attachment; filename=\"report.bin\"");
          exchange.sendResponseHeaders(200, REPORT.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(REPORT);
          }
        });
    this.server.createContext(
        "/slow.bin",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
          exchange
              .getResponseHeaders()
              .add("Content-Disposition", "attachment; filename=\"slow.bin\"");
          exchange.sendResponseHeaders(200, SLOW_SIZE);
          byte[] chunk = new byte[16 * 1024];
          try (OutputStream out = exchange.getResponseBody()) {
            for (int sent = 0; sent < SLOW_SIZE; sent += chunk.length) {
              out.write(chunk);
              out.flush();
              Thread.sleep(50);
            }
          } catch (IOException | InterruptedException _) {
            // The download was canceled, and the engine hung up.
          }
        });
    this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    this.server.start();
  }

  @AfterEach
  void stopServer() {
    // A test that another platform skipped never started it.
    if (this.server != null) {
      this.server.stop(0);
    }
  }

  @Test
  void downloadGoesWhereTheHandlerSaysAndReportsEachStep(@TempDir Path directory) throws Exception {
    Path target = directory.resolve("saved.bin");
    try (Application application = Application.create()) {
      Window window = this.openWithPage(application);
      BlockingQueue<DownloadRequest> asked = new LinkedBlockingQueue<>();
      window.downloadHandler(
          request -> {
            asked.add(request);
            return DownloadDecision.saveTo(target);
          });
      BlockingQueue<DownloadEvent> events = new LinkedBlockingQueue<>();
      window.onDownload(events::add);

      String url = this.url("/report.bin");
      window.navigate(url);

      DownloadRequest request = asked.poll(30, TimeUnit.SECONDS);
      Assertions.assertNotNull(request, "the handler is asked");
      Assertions.assertEquals(url, request.url().toString());
      Assertions.assertEquals("report.bin", request.suggestedFileName());
      List<DownloadEvent> heard = DownloadContractTest.untilEnd(events);
      Assertions.assertEquals(DownloadState.STARTED, heard.getFirst().state(), heard.toString());
      DownloadEvent last = heard.getLast();
      Assertions.assertEquals(DownloadState.COMPLETED, last.state(), heard.toString());
      Assertions.assertEquals(target, last.path());
      Assertions.assertEquals(REPORT.length, last.receivedBytes());
      Assertions.assertTrue(
          heard.stream().allMatch(event -> event.id() == last.id()), "one ID for the download");
      Assertions.assertArrayEquals(REPORT, Files.readAllBytes(target));
      Assertions.assertEquals(
          "completed:" + target,
          Loads.awaitValue(window, "window.__ended"),
          "the page hears the end, with the path");
    }
  }

  @Test
  void deniedDownloadWritesNothing(@TempDir Path directory) throws Exception {
    try (Application application = Application.create()) {
      Window window = this.openWithPage(application);
      BlockingQueue<DownloadRequest> asked = new LinkedBlockingQueue<>();
      window.downloadHandler(
          request -> {
            asked.add(request);
            return DownloadDecision.deny();
          });
      List<DownloadEvent> events = new CopyOnWriteArrayList<>();
      window.onDownload(events::add);

      window.navigate(this.url("/report.bin"));

      Assertions.assertNotNull(asked.poll(30, TimeUnit.SECONDS), "the handler is asked");
      Thread.sleep(2000);
      Assertions.assertEquals(List.of(), events, "a denied download has no steps");
      Assertions.assertEquals("ready", Loads.eval(window, "window.__state"), "the page stays");
      try (Stream<Path> files = Files.list(directory)) {
        Assertions.assertEquals(0, files.count());
      }
    }
  }

  @Test
  void canceledDownloadEndsCanceled(@TempDir Path directory) throws Exception {
    try (Application application = Application.create()) {
      Window window = this.openWithPage(application);
      window.downloadHandler(request -> DownloadDecision.saveTo(directory.resolve("slow.bin")));
      BlockingQueue<DownloadEvent> events = new LinkedBlockingQueue<>();
      window.onDownload(events::add);

      window.navigate(this.url("/slow.bin"));

      DownloadEvent started = events.poll(30, TimeUnit.SECONDS);
      Assertions.assertNotNull(started, "the download starts");
      Assertions.assertEquals(DownloadState.STARTED, started.state());
      Assertions.assertEquals(SLOW_SIZE, started.totalBytes(), "the size that the server sent");
      Assertions.assertEquals(
          "true",
          Loads.awaitValue(window, "window.__started > 0 ? true : undefined"),
          "the page hears the start");
      Loads.eval(
          window,
          "lwjwae.downloads.cancel(window.__started).then(still => window.__canceled = still);"
              + " undefined;");
      Assertions.assertEquals(
          "true",
          Loads.awaitValue(window, "window.__canceled"),
          "the page cancels a download that is still going");
      List<DownloadEvent> heard = DownloadContractTest.untilEnd(events);
      Assertions.assertEquals(DownloadState.CANCELED, heard.getLast().state(), heard.toString());
      Assertions.assertFalse(window.cancelDownload(started.id()), "an ended download is gone");
    }
  }

  /** Opens a window with a page that records what {@code lwjwae.downloads.listen} hears. */
  private Window openWithPage(Application application) throws Exception {
    Window window =
        application.open(WindowParameters.builder().title("lwjwae :: downloads").build());
    final var loaded = Loads.expectFinished(window);
    window.loadResource("test-app/index.html");
    window.show();
    loaded.get(30, TimeUnit.SECONDS);
    Loads.eval(
        window,
        """
        window.__state = "ready";
        lwjwae.downloads.listen((download) => {
          if (download.state === "started") window.__started = download.id;
          if (["completed", "failed", "canceled"].includes(download.state)) {
            window.__ended = download.state + ":" + download.path;
          }
        });
        undefined;
        """);
    return window;
  }

  private String url(String path) {
    return "http://127.0.0.1:" + this.server.getAddress().getPort() + path;
  }

  /** The events of one download, up to the one that ends it. */
  private static List<DownloadEvent> untilEnd(BlockingQueue<DownloadEvent> events)
      throws InterruptedException {
    List<DownloadEvent> heard = new CopyOnWriteArrayList<>();
    while (heard.isEmpty() || !heard.getLast().state().isFinal()) {
      DownloadEvent event = events.poll(60, TimeUnit.SECONDS);
      Assertions.assertNotNull(event, "the download ends; so far " + heard);
      heard.add(event);
    }
    return heard;
  }

  private static byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    new Random(7).nextBytes(bytes);
    return bytes;
  }
}
