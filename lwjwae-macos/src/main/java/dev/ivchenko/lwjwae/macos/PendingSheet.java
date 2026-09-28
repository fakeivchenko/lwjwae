package dev.ivchenko.lwjwae.macos;

import java.lang.foreign.Arena;
import java.util.function.LongConsumer;

/**
 * A sheet of a {@link MacWindow} that the user hasn't answered yet: a panel or an alert.
 *
 * @param answer Takes the {@code NSModalResponse} of the sheet.
 * @param memory The arena that holds the completion block. It's kept here so the block stays alive
 *     until AppKit has called it; the automatic arena frees it afterwards.
 */
record PendingSheet(LongConsumer answer, Arena memory) {}
