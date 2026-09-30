package dev.ivchenko.lwjwae.update;

import dev.ivchenko.lwjwae.exception.UpdateDownloadFailedException;
import dev.ivchenko.lwjwae.exception.UpdateRejectedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(20)
class UpdaterTest {
  private static final byte[] ARTIFACT =
      "the new version".repeat(10_000).getBytes(StandardCharsets.UTF_8);

  private static Updater updater(UpdateServer server, String current, UpdateInstaller installer) {
    return UpdaterTest.updater(server, current, installer, () -> {});
  }

  private static Updater updater(
      UpdateServer server, String current, UpdateInstaller installer, Runnable quit) {
    return new Updater(
        new UpdateParameters(server.url("/manifest.json"), server.publicKey(), current),
        installer,
        quit);
  }

  private static Throwable failure(CompletableFuture<?> future) {
    CompletionException thrown = Assertions.assertThrows(CompletionException.class, future::join);
    return thrown.getCause();
  }

  @Test
  void findsDownloadsAndInstallsNewerVersion(@TempDir Path directory) throws Exception {
    AtomicInteger quits = new AtomicInteger();
    RecordingInstaller installer = new RecordingInstaller(directory);
    try (UpdateServer server = new UpdateServer();
        Updater updater = UpdaterTest.updater(server, "1.2.0", installer, quits::incrementAndGet)) {
      server.release("1.3.0", "1.0.0", ARTIFACT);
      Update update = updater.check().join().orElseThrow();
      Assertions.assertEquals("1.3.0", update.version());
      Assertions.assertEquals("Faster", update.notes());
      Assertions.assertFalse(update.mandatory());
      Assertions.assertTrue(update.installable());
      Assertions.assertEquals(server.url("/app.bin"), update.artifact().url());

      List<Double> progress = new CopyOnWriteArrayList<>();
      DownloadedUpdate downloaded = updater.download(update, progress::add).join();
      Assertions.assertArrayEquals(ARTIFACT, Files.readAllBytes(downloaded.file()));
      Assertions.assertEquals(directory, downloaded.file().getParent());
      Assertions.assertEquals(0.0, progress.getFirst());
      Assertions.assertEquals(1.0, progress.getLast());
      Assertions.assertTrue(progress.size() > 10, "progress in steps: " + progress.size());

      updater.installAndRestart(downloaded);
      Assertions.assertEquals(List.of(downloaded.file()), installer.installed);
      Assertions.assertEquals(1, quits.get());
    }
  }

  @Test
  void sameOrOlderVersionIsNoUpdateAndOldOneIsMandatory(@TempDir Path directory) throws Exception {
    try (UpdateServer server = new UpdateServer()) {
      server.release("1.3.0", "1.1.0", ARTIFACT);
      RecordingInstaller installer = new RecordingInstaller(directory);
      try (Updater same = UpdaterTest.updater(server, "1.3.0", installer);
          Updater newer = UpdaterTest.updater(server, "2.0.0", installer);
          Updater old = UpdaterTest.updater(server, "1.0.9", installer)) {
        Assertions.assertEquals(Optional.empty(), same.check().join());
        Assertions.assertEquals(Optional.empty(), newer.check().join());
        Assertions.assertTrue(old.check().join().orElseThrow().mandatory());
      }
    }
  }

  @Test
  void manifestThatTheKeyDidNotSignIsRejected(@TempDir Path directory) throws Exception {
    try (UpdateServer server = new UpdateServer();
        UpdateServer impostor = new UpdateServer();
        Updater updater = UpdaterTest.updater(server, "1.0.0", new RecordingInstaller(directory))) {
      server.release("1.3.0", "1.0.0", ARTIFACT);
      impostor.release("9.0.0", "1.0.0", ARTIFACT);
      server.file("/manifest.json.sig", impostor.signature("/manifest.json"));
      Assertions.assertInstanceOf(
          UpdateRejectedException.class, UpdaterTest.failure(updater.check()));

      server.signed("/manifest.json", "{\"version\":\"2.0\"}".getBytes(StandardCharsets.UTF_8));
      Assertions.assertEquals(Optional.empty(), updater.check().join(), "no file for us");
      server.signed("/manifest.json", "[1]".getBytes(StandardCharsets.UTF_8));
      Assertions.assertInstanceOf(
          UpdateRejectedException.class, UpdaterTest.failure(updater.check()));
    }
  }

  @Test
  void fileThatIsNotTheSignedOneIsRejectedAndDeleted(@TempDir Path directory) throws Exception {
    try (UpdateServer server = new UpdateServer();
        Updater updater = UpdaterTest.updater(server, "1.0.0", new RecordingInstaller(directory))) {
      server.release("1.3.0", "1.0.0", ARTIFACT);
      Update update = updater.check().join().orElseThrow();
      byte[] tampered = ARTIFACT.clone();
      tampered[7] ^= 1;
      server.file("/app.bin", tampered);
      Assertions.assertInstanceOf(
          UpdateRejectedException.class, UpdaterTest.failure(updater.download(update)));
      server.file("/app.bin", new byte[ARTIFACT.length + 1]);
      Assertions.assertInstanceOf(
          UpdateRejectedException.class, UpdaterTest.failure(updater.download(update)));
      try (var left = Files.list(directory)) {
        Assertions.assertEquals(0, left.count(), "nothing stays behind");
      }
    }
  }

  @Test
  void missingManifestFailsTheDownload(@TempDir Path directory) throws Exception {
    try (UpdateServer server = new UpdateServer();
        Updater updater = UpdaterTest.updater(server, "1.0.0", new RecordingInstaller(directory))) {
      Assertions.assertInstanceOf(
          UpdateDownloadFailedException.class, UpdaterTest.failure(updater.check()));
    }
  }

  @Test
  void updateThatThisProcessCanNotInstallIsFoundButNotInstalled() throws Exception {
    try (UpdateServer server = new UpdateServer();
        Updater updater = UpdaterTest.updater(server, "1.0.0", new RecordingInstaller(null))) {
      server.release("1.3.0", "1.0.0", ARTIFACT);
      Update update = updater.check().join().orElseThrow();
      Assertions.assertFalse(update.installable());
      DownloadedUpdate downloaded = updater.download(update).join();
      Assertions.assertThrows(
          UnsupportedOperationException.class, () -> updater.installAndRestart(downloaded));
    }
  }
}
