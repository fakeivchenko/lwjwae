package dev.ivchenko.lwjwae.update;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.SneakyThrows;

/**
 * A server of updates for tests: it signs the manifests that it serves with a key of its own, as
 * the Gradle plugin signs them.
 */
public class UpdateServer implements AutoCloseable {
  private final HttpServer server;
  private final Map<String, byte[]> files = new ConcurrentHashMap<>();
  private final KeyPair keys;

  @SneakyThrows
  public UpdateServer() {
    this.keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    this.server.createContext(
        "/",
        exchange -> {
          byte[] body = this.files.get(exchange.getRequestURI().getPath());
          exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : body.length);
          if (body != null) {
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(body);
            }
          }
          exchange.close();
        });
    this.server.start();
  }

  /** The Base64 of the X.509 encoding of the public key, as the application trusts it. */
  public String publicKey() {
    return Base64.getEncoder().encodeToString(this.keys.getPublic().getEncoded());
  }

  public URI url(String path) {
    return URI.create("http://127.0.0.1:" + this.server.getAddress().getPort() + path);
  }

  /** What this server serves at {@code path}, such as a signature. */
  public byte[] signature(String path) {
    return this.files.get(path + ".sig");
  }

  /** Serves {@code bytes} at {@code path}. */
  public void file(String path, byte[] bytes) {
    this.files.put(path, bytes);
  }

  /**
   * Serves a manifest of {@code version} at {@code /manifest.json} and its signature, with {@code
   * artifact} at {@code /app.bin} for the platform of this process.
   */
  @SneakyThrows
  public void release(String version, String minimumVersion, byte[] artifact) {
    this.file("/app.bin", artifact);
    String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact));
    String manifest =
        "{\"version\":\"%s\",\"notes\":\"Faster\",\"minimumVersion\":\"%s\",\"artifacts\":{\"%s\":"
                .formatted(version, minimumVersion, UpdatePlatform.key())
            + "{\"url\":\"app.bin\",\"sha256\":\"%s\",\"size\":%d}}}"
                .formatted(sha256, artifact.length);
    this.signed("/manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
  }

  /** Serves {@code manifest} at {@code path} with its signature at {@code path.sig}. */
  @SneakyThrows
  public void signed(String path, byte[] manifest) {
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(this.keys.getPrivate());
    signer.update(manifest);
    this.file(path, manifest);
    this.file(
        path + ".sig",
        Base64.getEncoder().encodeToString(signer.sign()).getBytes(StandardCharsets.US_ASCII));
  }

  @Override
  public void close() throws IOException {
    this.server.stop(0);
  }
}
