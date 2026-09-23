package dev.ivchenko.lwjwae.macos;

import java.lang.foreign.Arena;
import java.util.concurrent.CompletableFuture;

/**
 * A completion block that the runtime holds and hasn't called yet.
 *
 * @param result The future that the block settles.
 * @param memory The arena that holds the block literal. It's kept here so the block stays alive
 *     until the runtime has called it; the automatic arena frees it afterwards.
 * @param <T> What the block reports.
 */
record PendingCompletion<T>(CompletableFuture<T> result, Arena memory) {}
