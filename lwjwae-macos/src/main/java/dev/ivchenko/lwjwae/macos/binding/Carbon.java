package dev.ivchenko.lwjwae.macos.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * The hot keys of the Carbon Event Manager: the one way for an application to hear a shortcut while
 * another has the keyboard, without the permission for accessibility that a monitor of {@code
 * NSEvent} needs. Carbon is deprecated as a whole, but these calls still back the global shortcuts
 * of the system, and every current macOS keeps them.
 *
 * <p>Every call runs on the main thread, where the application event target delivers the presses.
 */
@UtilityClass
public class Carbon {
  private final SymbolLookup CARBON =
      NativeLibraries.load("/System/Library/Frameworks/Carbon.framework/Carbon");

  /** {@code kEventClassKeyboard}: {@code 'keyb'}. */
  public final int EVENT_CLASS_KEYBOARD = 0x6B657962;

  /** {@code kEventHotKeyPressed}. */
  public final int EVENT_HOT_KEY_PRESSED = 5;

  /** {@code cmdKey}, {@code shiftKey}, {@code optionKey}, {@code controlKey}. */
  public final int COMMAND_KEY = 0x0100;

  public final int SHIFT_KEY = 0x0200;
  public final int OPTION_KEY = 0x0800;
  public final int CONTROL_KEY = 0x1000;

  /**
   * {@code noErr}, and {@code eventNotHandledErr} for an event that goes on to the next handler.
   */
  public final int NO_ERROR = 0;

  public final int EVENT_NOT_HANDLED = -9874;

  /** {@code kEventParamDirectObject}: {@code '----'}. */
  private final int PARAM_DIRECT_OBJECT = 0x2D2D2D2D;

  /** {@code typeEventHotKeyID}: {@code 'hkid'}. */
  private final int TYPE_EVENT_HOT_KEY_ID = 0x686B6964;

  /** The signature of the hot keys of lwjwae, {@code 'lwja'}, in each {@code EventHotKeyID}. */
  private final int SIGNATURE = 0x6C776A61;

  /** {@code kEventHotKeyExclusive}: the registration fails when another application has the key. */
  private final int EXCLUSIVE = 1;

  private final MethodHandle GET_APPLICATION_EVENT_TARGET =
      NativeLibraries.downcall(CARBON, "GetApplicationEventTarget", Signatures.POINTER_VOID);
  private final MethodHandle INSTALL_EVENT_HANDLER =
      NativeLibraries.downcall(CARBON, "InstallEventHandler", Signatures.INSTALL_EVENT_HANDLER);
  private final MethodHandle REGISTER_EVENT_HOT_KEY =
      NativeLibraries.downcall(CARBON, "RegisterEventHotKey", Signatures.REGISTER_EVENT_HOT_KEY);
  private final MethodHandle UNREGISTER_EVENT_HOT_KEY =
      NativeLibraries.downcall(CARBON, "UnregisterEventHotKey", Signatures.INT_POINTER);
  private final MethodHandle GET_EVENT_PARAMETER =
      NativeLibraries.downcall(CARBON, "GetEventParameter", Signatures.GET_EVENT_PARAMETER);

  /** Installs {@code handler}, an {@code EventHandlerUPP}, for every pressed hot key. */
  @SneakyThrows
  public void installHotKeyHandler(MemorySegment handler) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment types = arena.allocate(Signatures.C_INT, 2);
      types.setAtIndex(Signatures.C_INT, 0, EVENT_CLASS_KEYBOARD);
      types.setAtIndex(Signatures.C_INT, 1, EVENT_HOT_KEY_PRESSED);
      int status =
          (int)
              INSTALL_EVENT_HANDLER.invokeExact(
                  (MemorySegment) GET_APPLICATION_EVENT_TARGET.invokeExact(),
                  handler,
                  1L,
                  types,
                  MemorySegment.NULL,
                  MemorySegment.NULL);
      if (status != NO_ERROR) {
        throw new IllegalStateException("InstallEventHandler failed, status " + status);
      }
    }
  }

  /**
   * Registers the hot key of {@code keyCode} with {@code modifiers} under {@code id}.
   *
   * @return The {@code EventHotKeyRef}, or {@code NULL} when another application has the key.
   */
  @SneakyThrows
  public MemorySegment registerHotKey(int keyCode, int modifiers, int id) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment reference = arena.allocate(Signatures.C_POINTER);
      long hotKeyId = ((long) id << 32) | (SIGNATURE & 0xFFFFFFFFL);
      int status =
          (int)
              REGISTER_EVENT_HOT_KEY.invokeExact(
                  keyCode,
                  modifiers,
                  hotKeyId,
                  (MemorySegment) GET_APPLICATION_EVENT_TARGET.invokeExact(),
                  EXCLUSIVE,
                  reference);
      return status == NO_ERROR ? reference.get(Signatures.C_POINTER, 0) : MemorySegment.NULL;
    }
  }

  /** {@code UnregisterEventHotKey}. */
  @SneakyThrows
  public void unregisterHotKey(MemorySegment reference) {
    int _ = (int) UNREGISTER_EVENT_HOT_KEY.invokeExact(reference);
  }

  /** The ID of the hot key that {@code event} pressed, or -1 for a key of someone else. */
  @SneakyThrows
  public int hotKeyId(MemorySegment event) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment hotKeyId = arena.allocate(Signatures.C_INT, 2);
      int status =
          (int)
              GET_EVENT_PARAMETER.invokeExact(
                  event,
                  PARAM_DIRECT_OBJECT,
                  TYPE_EVENT_HOT_KEY_ID,
                  MemorySegment.NULL,
                  hotKeyId.byteSize(),
                  MemorySegment.NULL,
                  hotKeyId);
      if (status != NO_ERROR || hotKeyId.getAtIndex(Signatures.C_INT, 0) != SIGNATURE) {
        return -1;
      }
      return hotKeyId.getAtIndex(Signatures.C_INT, 1);
    }
  }
}
