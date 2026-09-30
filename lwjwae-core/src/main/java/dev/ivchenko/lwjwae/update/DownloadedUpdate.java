package dev.ivchenko.lwjwae.update;

import java.nio.file.Path;

/**
 * An update whose file is downloaded and checked, ready for {@link Updater#installAndRestart}.
 *
 * @param update The update.
 * @param file The file, with the size and the SHA-256 that the manifest gives.
 */
public record DownloadedUpdate(Update update, Path file) {}
