package dev.ivchenko.lwjwae.macos;

import java.lang.foreign.Arena;
import java.util.concurrent.CompletableFuture;

/**
 * An {@code evaluateJavaScript:} call that hasn't completed yet.
 *
 * @param result The future that the completion block settles.
 * @param memory The arena that holds the block literal. It's kept here so the block stays alive
 *     until WebKit has called it; the automatic arena frees it afterwards.
 */
record PendingEvaluation(CompletableFuture<String> result, Arena memory) {}
