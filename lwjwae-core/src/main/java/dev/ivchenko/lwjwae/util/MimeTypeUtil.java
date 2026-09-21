package dev.ivchenko.lwjwae.util;

import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Maps a filename to the media type that a web view needs to render the file.
 *
 * <p>This is a lookup table instead of {@link java.nio.file.Files#probeContentType}. Resources are
 * served from the classpath, where there's no file to probe, and a wrong type on the main document
 * makes the engine show markup as plain text.
 */
@UtilityClass
public class MimeTypeUtil {
  /** The type for a file that the table doesn't know. */
  public final String DEFAULT_TYPE = "application/octet-stream";

  private final Map<String, String> TYPES =
      Map.ofEntries(
          Map.entry("html", "text/html"),
          Map.entry("htm", "text/html"),
          Map.entry("css", "text/css"),
          Map.entry("js", "text/javascript"),
          Map.entry("mjs", "text/javascript"),
          Map.entry("map", "application/json"),
          Map.entry("json", "application/json"),
          Map.entry("webmanifest", "application/manifest+json"),
          Map.entry("xml", "application/xml"),
          Map.entry("svg", "image/svg+xml"),
          Map.entry("png", "image/png"),
          Map.entry("jpg", "image/jpeg"),
          Map.entry("jpeg", "image/jpeg"),
          Map.entry("gif", "image/gif"),
          Map.entry("webp", "image/webp"),
          Map.entry("avif", "image/avif"),
          Map.entry("ico", "image/x-icon"),
          Map.entry("woff", "font/woff"),
          Map.entry("woff2", "font/woff2"),
          Map.entry("ttf", "font/ttf"),
          Map.entry("otf", "font/otf"),
          Map.entry("txt", "text/plain"),
          Map.entry("pdf", "application/pdf"),
          Map.entry("mp3", "audio/mpeg"),
          Map.entry("ogg", "audio/ogg"),
          Map.entry("wav", "audio/wav"),
          Map.entry("mp4", "video/mp4"),
          Map.entry("webm", "video/webm"),
          Map.entry("wasm", "application/wasm"));

  /** Returns the media type for {@code path}, or {@link #DEFAULT_TYPE} when unknown. */
  public String of(String path) {
    int dot = path.lastIndexOf('.');
    if (dot < 0 || dot == path.length() - 1) {
      return DEFAULT_TYPE;
    }

    String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
    return TYPES.getOrDefault(extension, DEFAULT_TYPE);
  }
}
