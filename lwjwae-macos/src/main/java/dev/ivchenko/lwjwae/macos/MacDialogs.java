package dev.ivchenko.lwjwae.macos;

import dev.ivchenko.lwjwae.dialog.DialogCompletion;
import dev.ivchenko.lwjwae.dialog.FileType;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.macos.binding.Foundation;
import dev.ivchenko.lwjwae.macos.binding.ObjC;
import dev.ivchenko.lwjwae.macos.binding.Signatures;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;
import lombok.experimental.UtilityClass;

/**
 * The dialogs of a {@link MacWindow}, as sheets of the window: {@code NSOpenPanel}, {@code
 * NSSavePanel}, and {@code NSAlert}.
 *
 * <p>A sheet doesn't block: {@code beginSheetModalForWindow:completionHandler:} returns at once and
 * calls the block when the user answers, so the main thread keeps running the work of other
 * threads, which {@code runModal} would hold up until the answer. A cancellation ends the sheet
 * with {@code endSheet:}, which calls the block too. Every function here runs on the main thread.
 */
@UtilityClass
class MacDialogs {
  private final CallbackRegistry<PendingSheet> SHEETS = new CallbackRegistry<>();

  private final MemorySegment ON_SHEET_END =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          MacDialogs.class,
          "onSheetEnd",
          MethodType.methodType(void.class, MemorySegment.class, long.class),
          Signatures.SHEET_BLOCK);

  /** {@code NSModalResponseOK}. */
  private final long RESPONSE_OK = 1;

  /** {@code NSAlertFirstButtonReturn}: the default button, OK or yes. */
  private final long FIRST_BUTTON = 1000;

  // --- NSAlertStyle ---
  private final long ALERT_WARNING = 0;
  private final long ALERT_INFORMATIONAL = 1;
  private final long ALERT_CRITICAL = 2;

  /** Shows the panel that opens files or folders as a sheet of {@code window}. */
  void open(
      MemorySegment window,
      OpenDialogParameters parameters,
      DialogCompletion<List<Path>> completion) {
    MemorySegment panel = Foundation.retain(ObjC.send(ObjC.cls("NSOpenPanel"), "openPanel"));
    ObjC.sendVoid(panel, "setCanChooseFiles:", !parameters.directories());
    ObjC.sendVoid(panel, "setCanChooseDirectories:", parameters.directories());
    ObjC.sendVoid(panel, "setAllowsMultipleSelection:", parameters.multiple());
    MacDialogs.setUp(panel, parameters.title(), parameters.directory(), parameters.fileTypes());
    MacDialogs.present(
        window,
        panel,
        panel,
        completion,
        response ->
            completion.complete(response == RESPONSE_OK ? MacDialogs.paths(panel) : List.of()));
  }

  /** Shows the panel that saves a file as a sheet of {@code window}. */
  void save(
      MemorySegment window,
      SaveDialogParameters parameters,
      DialogCompletion<Optional<Path>> completion) {
    MemorySegment panel = Foundation.retain(ObjC.send(ObjC.cls("NSSavePanel"), "savePanel"));
    if (parameters.fileName() != null) {
      ObjC.sendVoid(panel, "setNameFieldStringValue:", Foundation.string(parameters.fileName()));
    }
    MacDialogs.setUp(panel, parameters.title(), parameters.directory(), parameters.fileTypes());
    MacDialogs.present(
        window,
        panel,
        panel,
        completion,
        response ->
            completion.complete(
                response == RESPONSE_OK
                    ? Optional.of(
                        Path.of(Foundation.string(MacDialogs.pathOf(ObjC.send(panel, "URL")))))
                    : Optional.empty()));
  }

  /** Shows an alert as a sheet of {@code window}. The title has no place on a sheet. */
  void message(
      MemorySegment window,
      MessageDialogParameters parameters,
      DialogCompletion<Boolean> completion) {
    MemorySegment alert = ObjC.send(ObjC.send(ObjC.cls("NSAlert"), "alloc"), "init");
    ObjC.sendVoid(alert, "setMessageText:", Foundation.string(parameters.message()));
    if (parameters.detail() != null) {
      ObjC.sendVoid(alert, "setInformativeText:", Foundation.string(parameters.detail()));
    }
    ObjC.sendVoid(
        alert,
        "setAlertStyle:",
        switch (parameters.level()) {
          case INFO, QUESTION -> ALERT_INFORMATIONAL;
          case WARNING -> ALERT_WARNING;
          case ERROR -> ALERT_CRITICAL;
        });
    // The first button is the default; a button titled Cancel answers the Escape key.
    List<String> buttons =
        switch (parameters.buttons()) {
          case OK -> List.of("OK");
          case OK_CANCEL -> List.of("OK", "Cancel");
          case YES_NO -> List.of("Yes", "No");
        };
    for (String title : buttons) {
      MemorySegment _ = ObjC.send(alert, "addButtonWithTitle:", Foundation.string(title));
    }
    MacDialogs.present(
        window,
        alert,
        ObjC.send(alert, "window"),
        completion,
        response -> completion.complete(response == FIRST_BUTTON));
  }

  /** The message, the folder, and the extensions of a panel. */
  private void setUp(MemorySegment panel, String title, Path directory, List<FileType> fileTypes) {
    if (title != null) {
      // A sheet has no title bar; the message stands at its top instead.
      ObjC.sendVoid(panel, "setMessage:", Foundation.string(title));
    }
    if (directory != null) {
      ObjC.sendVoid(
          panel,
          "setDirectoryURL:",
          ObjC.send(
              ObjC.cls("NSURL"),
              "fileURLWithPath:",
              Foundation.string(directory.toAbsolutePath().toString())));
    }
    List<String> extensions =
        fileTypes.stream().flatMap(type -> type.extensions().stream()).distinct().toList();
    // A panel has no menu of kinds, so it shows the files of every kind; a kind without extensions
    // stands for every file.
    boolean everyFile = fileTypes.stream().anyMatch(type -> type.extensions().isEmpty());
    if (!extensions.isEmpty() && !everyFile) {
      ObjC.sendVoid(panel, "setAllowedFileTypes:", MacDialogs.array(extensions));
    }
  }

  /**
   * Begins {@code sheet} over {@code window}. {@code owner}, the panel or the alert, is released
   * once the block has answered; a cancellation ends the sheet, which calls the block too.
   */
  private void present(
      MemorySegment window,
      MemorySegment owner,
      MemorySegment sheet,
      DialogCompletion<?> completion,
      LongConsumer answer) {
    Arena arena = Arena.ofAuto();
    long id =
        SHEETS.register(
            new PendingSheet(
                response -> {
                  try {
                    answer.accept(response);
                  } finally {
                    Foundation.release(owner);
                  }
                },
                arena));
    completion.onCancel(
        () -> {
          if (SHEETS.lookup(id) != null) {
            ObjC.sendVoid(window, "endSheet:", sheet);
          }
        });
    ObjC.sendVoid(
        owner,
        "beginSheetModalForWindow:completionHandler:",
        window,
        ObjC.block(arena, ON_SHEET_END, id));
  }

  /** The paths of the {@code URLs} of an open panel. */
  private List<Path> paths(MemorySegment panel) {
    MemorySegment urls = ObjC.send(panel, "URLs");
    long count = ObjC.sendLong(urls, "count");
    List<Path> paths = new ArrayList<>();
    for (long index = 0; index < count; index++) {
      paths.add(
          Path.of(Foundation.string(MacDialogs.pathOf(ObjC.send(urls, "objectAtIndex:", index)))));
    }
    return paths;
  }

  private MemorySegment pathOf(MemorySegment url) {
    return ObjC.send(url, "path");
  }

  /** An autoreleased {@code NSArray} of {@code NSString}. */
  private MemorySegment array(List<String> strings) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment objects = arena.allocate(Signatures.C_POINTER, strings.size());
      for (int index = 0; index < strings.size(); index++) {
        objects.setAtIndex(Signatures.C_POINTER, index, Foundation.string(strings.get(index)));
      }
      return ObjC.send(ObjC.cls("NSArray"), "arrayWithObjects:count:", objects, strings.size());
    }
  }

  /**
   * The completion block of a sheet: the user answered, or {@code endSheet:} ended it.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private void onSheetEnd(MemorySegment block, long response) {
    try {
      PendingSheet pending = SHEETS.unregister(ObjC.blockContext(block));
      if (pending != null) {
        pending.answer().accept(response);
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
