package dev.ivchenko.lwjwae.gtk;

import dev.ivchenko.lwjwae.dialog.DialogCompletion;
import dev.ivchenko.lwjwae.dialog.FileType;
import dev.ivchenko.lwjwae.dialog.MessageDialogParameters;
import dev.ivchenko.lwjwae.dialog.OpenDialogParameters;
import dev.ivchenko.lwjwae.dialog.SaveDialogParameters;
import dev.ivchenko.lwjwae.foreign.CallbackRegistry;
import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import dev.ivchenko.lwjwae.glib.binding.Glib;
import dev.ivchenko.lwjwae.glib.util.FileTypeUtil;
import dev.ivchenko.lwjwae.gtk.binding.Gtk;
import dev.ivchenko.lwjwae.gtk.binding.Signatures;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import lombok.experimental.UtilityClass;

/**
 * The dialogs of a {@link GtkWindow}: files through {@code GtkFileChooserNative}, and messages
 * through {@code GtkMessageDialog}.
 *
 * <p>A native file chooser is the dialog of the desktop portal inside a sandbox, where the
 * application can't see the files of the user until the user picks them, and GTK's own outside one.
 * Both dialogs answer through {@code response} and never block the GTK thread. A message dialog is
 * built with {@code g_object_new_with_properties} and its properties, since {@code
 * gtk_message_dialog_new} takes a {@code printf} format and variadic arguments. Every function here
 * runs on the GTK thread.
 */
@UtilityClass
class GtkDialogs {
  private final CallbackRegistry<IntConsumer> RESPONSES = new CallbackRegistry<>();

  private final MemorySegment ON_RESPONSE =
      NativeLibraries.upcall(
          MethodHandles.lookup(),
          GtkDialogs.class,
          "onResponse",
          MethodType.methodType(void.class, MemorySegment.class, int.class, MemorySegment.class),
          Signatures.RESPONSE_CALLBACK);

  /** The translations that GTK 3 labels its own buttons from. */
  private final String GTK_DOMAIN = "gtk30";

  // --- GtkMessageType ---
  private final int MESSAGE_INFO = 0;
  private final int MESSAGE_WARNING = 1;
  private final int MESSAGE_QUESTION = 2;
  private final int MESSAGE_ERROR = 3;

  /** Shows a file chooser that opens files or folders over {@code parent}. */
  void open(
      MemorySegment parent,
      OpenDialogParameters parameters,
      DialogCompletion<List<Path>> completion) {
    MemorySegment chooser =
        Gtk.fileChooserNativeNew(
            parameters.title(),
            parent,
            parameters.directories()
                ? Gtk.FILE_CHOOSER_ACTION_SELECT_FOLDER
                : Gtk.FILE_CHOOSER_ACTION_OPEN);
    Gtk.fileChooserSetSelectMultiple(chooser, parameters.multiple());
    GtkDialogs.startIn(chooser, parameters.directory());
    GtkDialogs.addFileTypes(chooser, parameters.fileTypes());
    GtkDialogs.present(
        chooser,
        completion,
        response ->
            completion.complete(
                response == Gtk.RESPONSE_ACCEPT
                    ? Gtk.fileChooserFilenames(chooser).stream().map(Path::of).toList()
                    : List.of()));
  }

  /** Shows a file chooser that saves a file over {@code parent}. */
  void save(
      MemorySegment parent,
      SaveDialogParameters parameters,
      DialogCompletion<Optional<Path>> completion) {
    MemorySegment chooser =
        Gtk.fileChooserNativeNew(parameters.title(), parent, Gtk.FILE_CHOOSER_ACTION_SAVE);
    Gtk.fileChooserSetOverwriteConfirmation(chooser, true);
    GtkDialogs.startIn(chooser, parameters.directory());
    if (parameters.fileName() != null) {
      Gtk.fileChooserSetCurrentName(chooser, parameters.fileName());
    }
    GtkDialogs.addFileTypes(chooser, parameters.fileTypes());
    GtkDialogs.present(
        chooser,
        completion,
        response ->
            completion.complete(
                response == Gtk.RESPONSE_ACCEPT
                    ? Gtk.fileChooserFilenames(chooser).stream().findFirst().map(Path::of)
                    : Optional.empty()));
  }

  /** Shows a message over {@code parent}, modal to it. */
  void message(
      MemorySegment parent,
      MessageDialogParameters parameters,
      DialogCompletion<Boolean> completion) {
    MemorySegment dialog = Glib.objectNew(Gtk.messageDialogType());
    Glib.setEnumProperty(
        dialog,
        "message-type",
        Gtk.messageTypeType(),
        switch (parameters.level()) {
          case INFO -> MESSAGE_INFO;
          case WARNING -> MESSAGE_WARNING;
          case ERROR -> MESSAGE_ERROR;
          case QUESTION -> MESSAGE_QUESTION;
        });
    Glib.setStringProperty(dialog, "text", parameters.message());
    if (parameters.detail() != null) {
      Glib.setStringProperty(dialog, "secondary-text", parameters.detail());
    }
    if (parameters.title() != null) {
      Gtk.windowSetTitle(dialog, parameters.title());
    }
    Gtk.windowSetTransientFor(dialog, parent);
    Gtk.windowSetModal(dialog, true);
    int yes =
        switch (parameters.buttons()) {
          case OK -> GtkDialogs.addButtons(dialog, null, "_OK", Gtk.RESPONSE_OK);
          case OK_CANCEL -> GtkDialogs.addButtons(dialog, "_Cancel", "_OK", Gtk.RESPONSE_OK);
          case YES_NO -> GtkDialogs.addButtons(dialog, "_No", "_Yes", Gtk.RESPONSE_YES);
        };
    long id =
        RESPONSES.register(
            response -> {
              Gtk.widgetDestroy(dialog);
              completion.complete(response == yes);
            });
    Glib.signalConnect(dialog, "response", ON_RESPONSE, CallbackRegistry.userData(id));
    completion.onCancel(
        () -> {
          if (RESPONSES.unregister(id) != null) {
            Gtk.widgetDestroy(dialog);
          }
        });
    Gtk.widgetShowAll(dialog);
  }

  /**
   * Adds the buttons in the order of GTK, the one that says no first, with the labels in the
   * language of the user.
   *
   * @return The response of the button that says yes, which is also the default.
   */
  private int addButtons(MemorySegment dialog, String no, String yes, int yesResponse) {
    if (no != null) {
      Gtk.dialogAddButton(
          dialog,
          Glib.translate(GTK_DOMAIN, no),
          yesResponse == Gtk.RESPONSE_YES ? Gtk.RESPONSE_NO : Gtk.RESPONSE_CANCEL);
    }
    Gtk.dialogAddButton(dialog, Glib.translate(GTK_DOMAIN, yes), yesResponse);
    Gtk.dialogSetDefaultResponse(dialog, yesResponse);
    return yesResponse;
  }

  private void startIn(MemorySegment chooser, Path directory) {
    if (directory != null) {
      Gtk.fileChooserSetCurrentFolder(chooser, directory.toAbsolutePath().toString());
    }
  }

  private void addFileTypes(MemorySegment chooser, List<FileType> fileTypes) {
    for (FileType type : fileTypes) {
      Gtk.fileChooserAddFilter(chooser, type.name(), FileTypeUtil.patterns(type));
    }
  }

  /**
   * Shows a native file chooser, which the caller owns: the answer and a cancellation both release
   * it, whichever comes first.
   */
  private void present(MemorySegment chooser, DialogCompletion<?> completion, IntConsumer answer) {
    long id =
        RESPONSES.register(
            response -> {
              try {
                answer.accept(response);
              } finally {
                Glib.unref(chooser);
              }
            });
    Glib.signalConnect(chooser, "response", ON_RESPONSE, CallbackRegistry.userData(id));
    completion.onCancel(
        () -> {
          if (RESPONSES.unregister(id) != null) {
            Gtk.nativeDialogHide(chooser);
            Glib.unref(chooser);
          }
        });
    Gtk.nativeDialogShow(chooser);
  }

  /**
   * {@code response} of a dialog: the user answered or closed it.
   *
   * <p>Suppressed warnings: {@code unused}: the method is reached only through the upcall stub that
   * binds it by name, so no Java code calls it and the compiler sees a dead private method.
   */
  @SuppressWarnings("unused")
  private void onResponse(MemorySegment dialog, int response, MemorySegment userData) {
    try {
      IntConsumer answer = RESPONSES.unregister(userData);
      if (answer != null) {
        answer.accept(response);
      }
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
