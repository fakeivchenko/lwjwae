package dev.ivchenko.lwjwae.update;

import dev.ivchenko.lwjwae.exception.UpdateRejectedException;
import dev.ivchenko.lwjwae.util.JsonUtil;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a manifest of updates says: the newest version, and the file that installs it on every
 * platform.
 *
 * <pre>{@code
 * {"version": "1.4.0", "notes": "...", "minimumVersion": "1.0.0",
 *  "artifacts": {"linux-x64": {"url": "app-1.4.0-x86_64.AppImage", "sha256": "...", "size": 1}}}
 * }</pre>
 *
 * @param version The newest version.
 * @param notes What changed, empty for nothing.
 * @param minimumVersion The oldest version that may keep running, or {@code null} for any.
 * @param artifacts The file of every platform, by the key of {@link UpdatePlatform#key()}.
 */
record UpdateManifest(
    String version, String notes, String minimumVersion, Map<String, UpdateArtifact> artifacts) {
  /**
   * Reads a manifest, resolving the URL of every file against {@code base}.
   *
   * @throws UpdateRejectedException If the text isn't a manifest.
   */
  static UpdateManifest parse(String json, URI base) {
    try {
      if (!(JsonUtil.parse(json) instanceof Map<?, ?> fields)) {
        throw new UpdateRejectedException("A manifest is a JSON object");
      }
      Map<String, UpdateArtifact> artifacts = new LinkedHashMap<>();
      if (fields.get("artifacts") instanceof Map<?, ?> byPlatform) {
        for (Map.Entry<?, ?> entry : byPlatform.entrySet()) {
          if (!(entry.getValue() instanceof Map<?, ?> artifact)) {
            throw new UpdateRejectedException("Not an artifact: " + entry.getKey());
          }
          artifacts.put(
              String.valueOf(entry.getKey()),
              new UpdateArtifact(
                  base.resolve(UpdateManifest.text(artifact, "url")),
                  UpdateManifest.text(artifact, "sha256").toLowerCase(Locale.ROOT),
                  UpdateManifest.number(artifact, "size")));
        }
      }
      Object notes = fields.get("notes");
      Object minimum = fields.get("minimumVersion");
      return new UpdateManifest(
          UpdateManifest.text(fields, "version"),
          notes instanceof String text ? text : "",
          minimum instanceof String text ? text : null,
          artifacts);
    } catch (IllegalArgumentException e) {
      throw new UpdateRejectedException("Not a manifest: " + e.getMessage(), e);
    }
  }

  private static String text(Map<?, ?> fields, String key) {
    if (!(fields.get(key) instanceof String text) || text.isBlank()) {
      throw new UpdateRejectedException("The manifest has no " + key);
    }
    return text;
  }

  private static long number(Map<?, ?> fields, String key) {
    if (!(fields.get(key) instanceof Number number) || number.longValue() < 0) {
      throw new UpdateRejectedException("The manifest has no " + key);
    }
    return number.longValue();
  }
}
