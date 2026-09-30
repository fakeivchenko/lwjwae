package dev.ivchenko.lwjwae.update;

import dev.ivchenko.lwjwae.exception.UpdateDownloadFailedException;
import dev.ivchenko.lwjwae.exception.UpdateRejectedException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * Finds, downloads, and installs the updates of an application, from a manifest at any URL.
 *
 * <pre>{@code
 * Updater updater = application.updater();
 * updater.check().thenAccept(found -> found.ifPresent(update -> {
 *   DownloadedUpdate downloaded = updater.download(update, application::progress).join();
 *   updater.installAndRestart(downloaded);
 * }));
 * }</pre>
 *
 * <p>The manifest is a JSON file that the {@code updateManifest} task of the Gradle plugin writes,
 * next to its Ed25519 signature, which {@link #check()} verifies with the public key of the
 * application before it believes a word of it. The manifest names a file for every platform with
 * its size and SHA-256, and {@link #download} checks both, so what gets installed is exactly what
 * the private key signed for. A version that isn't newer than the running one is no update, so a
 * manifest can't take the application back.
 *
 * <p>Each platform installs its own package: the {@code .msi} on Windows, the {@code .app} bundle
 * on macOS, and the AppImage on Linux. {@link #installAndRestart} hands the file to a process of
 * the system that waits for the application to quit, puts the new version in place, and starts it.
 * A Debian or Arch package is updated by its package manager, so there {@link #check()} still finds
 * an update, but not an {@link Update#installable() installable} one.
 *
 * <p>Every method is safe to call from any thread. The network calls run on virtual threads.
 */
public final class Updater implements AutoCloseable {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
  private static final int BUFFER_SIZE = 64 * 1024;
  private static final double PROGRESS_STEP = 0.01;

  private final UpdateParameters parameters;
  private final UpdateInstaller installer;
  private final Runnable quit;
  private final HttpClient client;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Creates an updater. An application has one, from {@link
   * dev.ivchenko.lwjwae.Application#updater()}.
   *
   * @param quit Quits the application, once the installer waits for it.
   */
  public Updater(UpdateParameters parameters, Runnable quit) {
    this(parameters, UpdatePlatform.installer(), quit);
  }

  Updater(UpdateParameters parameters, UpdateInstaller installer, Runnable quit) {
    this.parameters = Objects.requireNonNull(parameters, "parameters");
    this.installer = installer;
    this.quit = Objects.requireNonNull(quit, "quit");
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(this.executor)
            .build();
  }

  /** The version that runs. */
  public String currentVersion() {
    return this.parameters.currentVersion();
  }

  /**
   * Reads the manifest and its signature, and returns the update that it offers for this platform,
   * if its version is newer than the running one.
   *
   * <p>The future fails with an {@link UpdateRejectedException} for a signature that isn't the one
   * of the key, or a manifest that isn't one, and with an {@link UpdateDownloadFailedException}
   * when either can't be downloaded.
   */
  public CompletableFuture<Optional<Update>> check() {
    return this.async(this::checkNow);
  }

  /**
   * Downloads the file of {@code update} and checks its size and SHA-256.
   *
   * @param progress Hears how much is downloaded, from 0 to 1, in steps of a hundredth; on the
   *     thread of the download.
   */
  public CompletableFuture<DownloadedUpdate> download(Update update, DoubleConsumer progress) {
    Objects.requireNonNull(update, "update");
    Objects.requireNonNull(progress, "progress");
    return this.async(() -> this.downloadNow(update, progress));
  }

  /** The same as {@link #download(Update, DoubleConsumer)}, without hearing the progress. */
  public CompletableFuture<DownloadedUpdate> download(Update update) {
    return this.download(update, _ -> {});
  }

  /**
   * Starts the installer of {@code downloaded}, which waits for the application to exit, and quits
   * the application. The installer starts the new version.
   *
   * @throws UnsupportedOperationException If the update isn't {@link Update#installable()}.
   * @throws UpdateRejectedException If a bundle of macOS fails {@code codesign}.
   * @throws UncheckedIOException If the installer can't start.
   */
  public void installAndRestart(DownloadedUpdate downloaded) {
    Objects.requireNonNull(downloaded, "downloaded");
    if (!downloaded.update().installable()) {
      throw new UnsupportedOperationException(
          "This application can't install its updates: a package manager updates it, or it"
              + " doesn't run from its package");
    }
    try {
      this.installer.install(downloaded.file(), ProcessHandle.current().pid());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    this.quit.run();
  }

  /**
   * Stops what runs, which the application does when it quits. It doesn't wait: {@link
   * #installAndRestart} quits the application from a thread of the updater itself.
   */
  @Override
  public void close() {
    this.client.shutdownNow();
    this.executor.shutdownNow();
  }

  private Optional<Update> checkNow() {
    URI manifestUrl = this.parameters.manifestUrl();
    byte[] manifest = this.fetch(manifestUrl);
    byte[] signature = this.fetch(URI.create(manifestUrl + ".sig"));
    UpdateSignatures.verify(this.parameters.publicKey(), manifest, signature);
    UpdateManifest parsed =
        UpdateManifest.parse(new String(manifest, StandardCharsets.UTF_8), manifestUrl);
    String current = this.parameters.currentVersion();
    if (VersionOrder.compare(parsed.version(), current) <= 0) {
      return Optional.empty();
    }
    UpdateArtifact artifact = parsed.artifacts().get(UpdatePlatform.key());
    if (artifact == null) {
      return Optional.empty();
    }
    boolean mandatory =
        parsed.minimumVersion() != null
            && VersionOrder.compare(current, parsed.minimumVersion()) < 0;
    return Optional.of(
        new Update(
            parsed.version(), parsed.notes(), mandatory, artifact, this.installer.isInstallable()));
  }

  private DownloadedUpdate downloadNow(Update update, DoubleConsumer progress) {
    UpdateArtifact artifact = update.artifact();
    String name = Path.of(artifact.url().getPath()).getFileName().toString();
    try {
      Path directory =
          this.installer.isInstallable()
              ? this.installer.downloadDirectory()
              : Files.createTempDirectory("lwjwae-update");
      Path file = directory.resolve(".lwjwae-update-" + name);
      Path partial = directory.resolve(file.getFileName() + ".part");
      HttpResponse<InputStream> response =
          this.client.send(
              HttpRequest.newBuilder(artifact.url()).GET().build(),
              HttpResponse.BodyHandlers.ofInputStream());
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long written = 0;
      try (InputStream in = response.body();
          OutputStream out = Files.newOutputStream(partial)) {
        Updater.requireOk(response, artifact.url());
        byte[] buffer = new byte[BUFFER_SIZE];
        double reported = 0;
        progress.accept(0);
        for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
          written += read;
          if (written > artifact.size()) {
            break;
          }
          digest.update(buffer, 0, read);
          out.write(buffer, 0, read);
          double fraction = artifact.size() == 0 ? 1 : (double) written / artifact.size();
          if (fraction - reported >= PROGRESS_STEP) {
            reported = fraction;
            progress.accept(Math.min(1, fraction));
          }
        }
      }
      String sha256 = HexFormat.of().formatHex(digest.digest());
      if (written != artifact.size() || !sha256.equals(artifact.sha256())) {
        Files.deleteIfExists(partial);
        throw new UpdateRejectedException(
            "The download of " + artifact.url() + " isn't the file that the manifest signs for");
      }
      Files.move(
          partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      progress.accept(1);
      return new DownloadedUpdate(update, file);
    } catch (IOException e) {
      throw new UpdateDownloadFailedException("Can't download " + artifact.url(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UpdateDownloadFailedException("Interrupted while downloading " + artifact.url(), e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private byte[] fetch(URI url) {
    try {
      HttpResponse<byte[]> response =
          this.client.send(
              HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
      Updater.requireOk(response, url);
      return response.body();
    } catch (IOException e) {
      throw new UpdateDownloadFailedException("Can't download " + url, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UpdateDownloadFailedException("Interrupted while downloading " + url, e);
    }
  }

  private static void requireOk(HttpResponse<?> response, URI url) {
    if (response.statusCode() != 200) {
      throw new UpdateDownloadFailedException(
          "Can't download " + url + ": HTTP " + response.statusCode());
    }
  }

  private <T> CompletableFuture<T> async(Supplier<T> action) {
    return CompletableFuture.supplyAsync(action, this.executor);
  }
}
