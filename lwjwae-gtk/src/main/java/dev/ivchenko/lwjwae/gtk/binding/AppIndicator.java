package dev.ivchenko.lwjwae.gtk.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bindings to the subset of libappindicator that the tray needs: the StatusNotifierItem protocol
 * that the panels of KDE, GNOME (with its extension), and most others speak, and the only tray that
 * works on Wayland.
 *
 * <p>The library is optional. It isn't part of GTK, so a machine may not have it, and the tray
 * falls back to {@code GtkStatusIcon} then. Everything here must be called on the GTK thread, and
 * only after {@link #isAvailable()} returned {@code true}.
 */
@UtilityClass
public class AppIndicator {
  private final SymbolLookup INDICATOR =
      NativeLibraries.loadIfPresent(
          "libayatana-appindicator3.so.1", "libappindicator3.so.1", "libayatana-appindicator3.so");

  /** {@code APP_INDICATOR_CATEGORY_APPLICATION_STATUS}. */
  private final int CATEGORY_APPLICATION_STATUS = 0;

  /** {@code APP_INDICATOR_STATUS_PASSIVE}: the icon is hidden. */
  private final int STATUS_PASSIVE = 0;

  /** {@code APP_INDICATOR_STATUS_ACTIVE}: the icon is shown. */
  private final int STATUS_ACTIVE = 1;

  private final MethodHandle NEW =
      NativeLibraries.downcallIfPresent(
          INDICATOR, "app_indicator_new", Signatures.POINTER_POINTER_POINTER_INT);
  private final MethodHandle SET_STATUS =
      NativeLibraries.downcallIfPresent(
          INDICATOR, "app_indicator_set_status", Signatures.VOID_POINTER_INT);
  private final MethodHandle SET_MENU =
      NativeLibraries.downcallIfPresent(
          INDICATOR, "app_indicator_set_menu", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle SET_ICON_THEME_PATH =
      NativeLibraries.downcallIfPresent(
          INDICATOR, "app_indicator_set_icon_theme_path", Signatures.VOID_POINTER_POINTER);
  private final MethodHandle SET_ICON_FULL =
      NativeLibraries.downcallIfPresent(
          INDICATOR, "app_indicator_set_icon_full", Signatures.VOID_POINTER_POINTER_POINTER);
  private final MethodHandle SET_TITLE =
      NativeLibraries.downcallIfPresent(
          INDICATOR, "app_indicator_set_title", Signatures.VOID_POINTER_POINTER);

  /** Whether the library loaded. */
  public boolean isAvailable() {
    return INDICATOR != null;
  }

  /**
   * Calls {@code app_indicator_new} with the application-status category. {@code iconName} is a
   * file name without extension inside the theme path, see {@link #setIconThemePath}.
   */
  @SneakyThrows
  public MemorySegment create(String id, String iconName) {
    try (Arena arena = Arena.ofConfined()) {
      return (MemorySegment)
          NEW.invokeExact(
              arena.allocateFrom(id), arena.allocateFrom(iconName), CATEGORY_APPLICATION_STATUS);
    }
  }

  /** Shows or hides the indicator. */
  @SneakyThrows
  public void setActive(MemorySegment indicator, boolean active) {
    SET_STATUS.invokeExact(indicator, active ? STATUS_ACTIVE : STATUS_PASSIVE);
  }

  /** Calls {@code app_indicator_set_menu}. The indicator takes a reference to the menu. */
  @SneakyThrows
  public void setMenu(MemorySegment indicator, MemorySegment menu) {
    SET_MENU.invokeExact(indicator, menu);
  }

  /** Calls {@code app_indicator_set_icon_theme_path}: the directory that holds the icon files. */
  @SneakyThrows
  public void setIconThemePath(MemorySegment indicator, String directory) {
    try (Arena arena = Arena.ofConfined()) {
      SET_ICON_THEME_PATH.invokeExact(indicator, arena.allocateFrom(directory));
    }
  }

  /**
   * Calls {@code app_indicator_set_icon_full}. The name must differ from the previous one for the
   * host to reload the image, because hosts cache by name.
   */
  @SneakyThrows
  public void setIcon(MemorySegment indicator, String iconName, String description) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment text =
          description == null ? MemorySegment.NULL : arena.allocateFrom(description);
      SET_ICON_FULL.invokeExact(indicator, arena.allocateFrom(iconName), text);
    }
  }

  /** Calls {@code app_indicator_set_title}: what a host shows as the tooltip or in its list. */
  @SneakyThrows
  public void setTitle(MemorySegment indicator, String title) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment text = title == null ? MemorySegment.NULL : arena.allocateFrom(title);
      SET_TITLE.invokeExact(indicator, text);
    }
  }
}
