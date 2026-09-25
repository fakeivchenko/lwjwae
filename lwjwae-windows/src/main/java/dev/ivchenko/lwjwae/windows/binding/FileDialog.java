package dev.ivchenko.lwjwae.windows.binding;

import dev.ivchenko.lwjwae.dialog.FileType;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The common item dialogs of Windows: {@code IFileOpenDialog} and {@code IFileSaveDialog}, with the
 * {@code IShellItem} and {@code IShellItemArray} that they answer with.
 *
 * <p>The slots are those of {@code shobjidl_core.h}: {@code IModalWindow::Show} first, then the
 * methods of {@code IFileDialog} that both dialogs share, then those of the open or the save
 * dialog. Every function here must be called on the UI thread, in its single-threaded apartment.
 */
@UtilityClass
public class FileDialog {
  public final MemorySegment IID_SHELL_ITEM = Com.guid("43826d1e-e718-42ee-bc55-a1e261c37bfe");

  private final MemorySegment CLSID_FILE_OPEN_DIALOG =
      Com.guid("dc1c5a9c-e88a-4dde-a5a1-60f82a20aef7");
  private final MemorySegment CLSID_FILE_SAVE_DIALOG =
      Com.guid("c0b4e2f3-ba21-4773-8dba-335ec946eb8b");
  private final MemorySegment IID_FILE_OPEN_DIALOG =
      Com.guid("d57c7288-d4ad-4768-be02-9d969532d960");
  private final MemorySegment IID_FILE_SAVE_DIALOG =
      Com.guid("84bccd23-5fde-4cdb-aea4-af64b83d78ab");

  // --- IModalWindow and IFileDialog ---
  private final int SHOW = 3;
  private final int SET_FILE_TYPES = 4;
  private final int SET_OPTIONS = 9;
  private final int GET_OPTIONS = 10;
  private final int SET_FOLDER = 12;
  private final int SET_FILE_NAME = 15;
  private final int SET_TITLE = 17;
  private final int GET_RESULT = 20;
  private final int SET_DEFAULT_EXTENSION = 22;
  // IFileOpenDialog
  private final int GET_RESULTS = 27;
  // IShellItemArray
  private final int ARRAY_GET_COUNT = 7;
  private final int ARRAY_GET_ITEM_AT = 8;
  // IShellItem
  private final int ITEM_GET_DISPLAY_NAME = 5;

  // --- FILEOPENDIALOGOPTIONS ---
  public final int FOS_OVERWRITEPROMPT = 0x2;
  public final int FOS_PICKFOLDERS = 0x20;
  public final int FOS_FORCEFILESYSTEM = 0x40;
  public final int FOS_ALLOWMULTISELECT = 0x200;
  public final int FOS_PATHMUSTEXIST = 0x800;
  public final int FOS_FILEMUSTEXIST = 0x1000;

  /** {@code SIGDN_FILESYSPATH}: the path of an item in the file system. */
  private final int SIGDN_FILESYSPATH = 0x80058000;

  /**
   * {@code HRESULT_FROM_WIN32(ERROR_CANCELLED)}: what {@code Show} answers when the user cancels.
   */
  public final int CANCELLED = 0x800704C7;

  /** A new open dialog, which the caller releases. */
  public MemorySegment newOpenDialog() {
    return Ole32.coCreateInstance(CLSID_FILE_OPEN_DIALOG, IID_FILE_OPEN_DIALOG);
  }

  /** A new save dialog, which the caller releases. */
  public MemorySegment newSaveDialog() {
    return Ole32.coCreateInstance(CLSID_FILE_SAVE_DIALOG, IID_FILE_SAVE_DIALOG);
  }

  /** Adds {@code options}, {@code FOS_*} flags, to the ones that the dialog has by default. */
  public void addOptions(MemorySegment dialog, int options) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment current = arena.allocate(Signatures.C_INT);
      Com.check("GetOptions", Com.call(dialog, GET_OPTIONS, current));
      Com.check(
          "SetOptions", Com.call(dialog, SET_OPTIONS, current.get(Signatures.C_INT, 0) | options));
    }
  }

  /** Calls {@code SetTitle}. */
  public void setTitle(MemorySegment dialog, String title) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check("SetTitle", Com.call(dialog, SET_TITLE, Wide.allocate(arena, title)));
    }
  }

  /** Calls {@code SetFileName}: the name that the dialog proposes. */
  public void setFileName(MemorySegment dialog, String name) {
    try (Arena arena = Arena.ofConfined()) {
      Com.check("SetFileName", Com.call(dialog, SET_FILE_NAME, Wide.allocate(arena, name)));
    }
  }

  /** Calls {@code SetFolder}: the folder that the dialog opens in, whatever it remembers. */
  public void setFolder(MemorySegment dialog, String folder) {
    MemorySegment item = Shell32.shellItem(folder);
    try {
      Com.check("SetFolder", Com.call(dialog, SET_FOLDER, item));
    } finally {
      Com.release(item);
    }
  }

  /**
   * Calls {@code SetFileTypes} with one {@code COMDLG_FILTERSPEC} per kind of file, its extensions
   * as {@code *.png;*.jpg}, and a save dialog appends the first extension of the chosen kind to a
   * name that has none, through {@code SetDefaultExtension}.
   */
  public void setFileTypes(MemorySegment dialog, List<FileType> types) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment specs = arena.allocate(Signatures.C_POINTER, 2L * types.size());
      for (int index = 0; index < types.size(); index++) {
        FileType type = types.get(index);
        String spec =
            type.extensions().isEmpty()
                ? "*.*"
                : type.extensions().stream()
                    .map(extension -> "*." + extension)
                    .collect(Collectors.joining(";"));
        specs.setAtIndex(Signatures.C_POINTER, 2L * index, Wide.allocate(arena, type.name()));
        specs.setAtIndex(Signatures.C_POINTER, 2L * index + 1, Wide.allocate(arena, spec));
      }
      Com.check("SetFileTypes", Com.call(dialog, SET_FILE_TYPES, types.size(), specs));
      types.stream()
          .flatMap(type -> type.extensions().stream())
          .findFirst()
          .ifPresent(
              extension ->
                  Com.check(
                      "SetDefaultExtension",
                      Com.call(dialog, SET_DEFAULT_EXTENSION, Wide.allocate(arena, extension))));
    }
  }

  /**
   * Calls {@code Show} over {@code owner}, which runs a modal loop until the user answers.
   *
   * @return {@link Com#S_OK} for an answer, {@link #CANCELLED} for none.
   */
  public int show(MemorySegment dialog, MemorySegment owner) {
    return Com.call(dialog, SHOW, owner);
  }

  /** The path that a dialog picked, from {@code GetResult}. */
  public String result(MemorySegment dialog) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("GetResult", Com.call(dialog, GET_RESULT, out));
      MemorySegment item = Com.pointerAt(out);
      try {
        return path(item);
      } finally {
        Com.release(item);
      }
    }
  }

  /** The paths that an open dialog picked, from {@code GetResults}. */
  public List<String> results(MemorySegment dialog) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("GetResults", Com.call(dialog, GET_RESULTS, out));
      MemorySegment array = Com.pointerAt(out);
      try {
        MemorySegment count = arena.allocate(Signatures.C_INT);
        Com.check("GetCount", Com.call(array, ARRAY_GET_COUNT, count));
        List<String> paths = new ArrayList<>();
        for (int index = 0; index < count.get(Signatures.C_INT, 0); index++) {
          MemorySegment itemOut = arena.allocate(Signatures.C_POINTER);
          Com.check("GetItemAt", Com.call(array, ARRAY_GET_ITEM_AT, index, itemOut));
          MemorySegment item = Com.pointerAt(itemOut);
          try {
            paths.add(path(item));
          } finally {
            Com.release(item);
          }
        }
        return paths;
      } finally {
        Com.release(array);
      }
    }
  }

  private String path(MemorySegment item) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment out = arena.allocate(Signatures.C_POINTER);
      Com.check("GetDisplayName", Com.call(item, ITEM_GET_DISPLAY_NAME, SIGDN_FILESYSPATH, out));
      return Wide.take(Com.pointerAt(out));
    }
  }
}
