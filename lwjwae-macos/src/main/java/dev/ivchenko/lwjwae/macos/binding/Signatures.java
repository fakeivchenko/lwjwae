package dev.ivchenko.lwjwae.macos.binding;

import dev.ivchenko.lwjwae.foreign.Layouts;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import lombok.experimental.UtilityClass;

/**
 * Every native signature that the macOS backend binds, in one place.
 *
 * <p>Almost everything here is a shape of {@code objc_msgSend}. The trampoline of the runtime takes
 * whatever the target method takes, so one downcall handle per distinct signature serves every
 * selector of that signature. The receiver and the selector come first in each.
 *
 * <p>No method that returns a struct is bound. On x86_64, such a call must go through {@code
 * objc_msgSend_stret}, which doesn't exist on arm64. Avoiding struct returns keeps the bindings
 * identical on both architectures.
 */
@UtilityClass
public class Signatures {
  /** {@code NSInteger}, {@code NSUInteger}: 64-bit on every supported Mac. */
  public final ValueLayout.OfLong C_LONG = Layouts.C_LONG_LONG;

  /** {@code int}. */
  public final ValueLayout.OfInt C_INT = Layouts.C_INT;

  /** {@code short}. */
  public final ValueLayout.OfShort C_SHORT = Layouts.C_SHORT;

  /** {@code CGFloat}. */
  public final ValueLayout.OfDouble C_DOUBLE = Layouts.C_DOUBLE;

  /** {@code BOOL}: one byte on both architectures. */
  public final ValueLayout.OfBoolean C_BOOL = Layouts.C_BOOL;

  /** {@code id}, {@code SEL}, {@code Class}, any {@code T*}. */
  public final AddressLayout C_POINTER = Layouts.C_POINTER;

  // --- structs, laid out flat: the ABI treats them exactly as it treats the nested originals ---

  /** {@code NSPoint}. */
  public final StructLayout NSPOINT =
      MemoryLayout.structLayout(C_DOUBLE.withName("x"), C_DOUBLE.withName("y"));

  /** {@code NSSize}. */
  public final StructLayout NSSIZE =
      MemoryLayout.structLayout(C_DOUBLE.withName("width"), C_DOUBLE.withName("height"));

  /** {@code NSRect}. */
  public final StructLayout NSRECT =
      MemoryLayout.structLayout(
          C_DOUBLE.withName("x"),
          C_DOUBLE.withName("y"),
          C_DOUBLE.withName("width"),
          C_DOUBLE.withName("height"));

  /** An Objective-C block literal with one captured {@code long}. See {@link ObjC#block}. */
  public final StructLayout BLOCK =
      MemoryLayout.structLayout(
          C_POINTER.withName("isa"),
          C_INT.withName("flags"),
          C_INT.withName("reserved"),
          C_POINTER.withName("invoke"),
          C_POINTER.withName("descriptor"),
          C_LONG.withName("context"));

  /** The descriptor that every block literal points to. */
  public final StructLayout BLOCK_DESCRIPTOR =
      MemoryLayout.structLayout(C_LONG.withName("reserved"), C_LONG.withName("size"));

  // --- C functions ---

  /** {@code int f(void)}. */
  public final FunctionDescriptor INT_VOID = FunctionDescriptor.of(C_INT);

  /** {@code T* f(void)}. */
  public final FunctionDescriptor POINTER_VOID = FunctionDescriptor.of(C_POINTER);

  /** {@code void f(T*)}. */
  public final FunctionDescriptor VOID_POINTER = FunctionDescriptor.ofVoid(C_POINTER);

  /** {@code T* f(U*)}. */
  public final FunctionDescriptor POINTER_POINTER = FunctionDescriptor.of(C_POINTER, C_POINTER);

  /** {@code void f(T*, U*, V*)}. */
  public final FunctionDescriptor VOID_POINTER_POINTER_POINTER =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER);

  /** {@code Class objc_allocateClassPair(Class, const char*, size_t)}. */
  public final FunctionDescriptor POINTER_POINTER_POINTER_LONG =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_LONG);

  /** {@code BOOL class_addMethod(Class, SEL, IMP, const char*)}. */
  public final FunctionDescriptor BOOL_POINTER_POINTER_POINTER_POINTER =
      FunctionDescriptor.of(C_BOOL, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  // --- objc_msgSend shapes: receiver, selector, then the method's own arguments ---

  /** {@code id -[receiver selector]}. */
  public final FunctionDescriptor MSG_ID = FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER);

  /** {@code void -[receiver selector]}. */
  public final FunctionDescriptor MSG_VOID = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER);

  /** {@code NSInteger -[receiver selector]}. */
  public final FunctionDescriptor MSG_LONG = FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER);

  /** {@code id -[receiver selector:id]}. */
  public final FunctionDescriptor MSG_ID_ID =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code id -[receiver selector:CGFloat]}. */
  public final FunctionDescriptor MSG_ID_DOUBLE =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_DOUBLE);

  /** {@code id -[receiver selector:NSInteger]}, the shape of {@code objc_allocateClassPair}. */
  public final FunctionDescriptor MSG_ID_LONG = POINTER_POINTER_POINTER_LONG;

  /** {@code id -[receiver selector:BOOL]}. */
  public final FunctionDescriptor MSG_ID_BOOL =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_BOOL);

  /** {@code void -[receiver selector:id]}. */
  public final FunctionDescriptor MSG_VOID_ID =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER);

  /** {@code void -[receiver selector:NSInteger]}. */
  public final FunctionDescriptor MSG_VOID_LONG =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_LONG);

  /** {@code void -[receiver selector:BOOL]}. */
  public final FunctionDescriptor MSG_VOID_BOOL =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_BOOL);

  /** {@code BOOL -[receiver selector]}. */
  public final FunctionDescriptor MSG_BOOL = FunctionDescriptor.of(C_BOOL, C_POINTER, C_POINTER);

  /** {@code BOOL -[receiver selector:id]}. */
  public final FunctionDescriptor MSG_BOOL_ID =
      FunctionDescriptor.of(C_BOOL, C_POINTER, C_POINTER, C_POINTER);

  /** {@code BOOL -[receiver selector:NSInteger]}. */
  public final FunctionDescriptor MSG_BOOL_LONG =
      FunctionDescriptor.of(C_BOOL, C_POINTER, C_POINTER, C_LONG);

  /** {@code id -[receiver selector:id selector:id]}. */
  public final FunctionDescriptor MSG_ID_ID_ID =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code void -[receiver selector:id selector:id]}. */
  public final FunctionDescriptor MSG_VOID_ID_ID =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code void -[receiver selector:id selector:BOOL]}. */
  public final FunctionDescriptor MSG_VOID_ID_BOOL =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_BOOL);

  /** {@code void -[receiver selector:SEL selector:id selector:BOOL]}. */
  public final FunctionDescriptor MSG_VOID_SEL_ID_BOOL =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_BOOL);

  /** {@code id -[receiver selector:const void* selector:NSUInteger]}. */
  public final FunctionDescriptor MSG_ID_POINTER_LONG =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG);

  /** {@code void -[receiver selector:void* selector:NSUInteger]}. */
  public final FunctionDescriptor MSG_VOID_POINTER_LONG =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_LONG);

  /** {@code id -[receiver selector:id selector:NSInteger selector:BOOL]}. */
  public final FunctionDescriptor MSG_ID_ID_LONG_BOOL =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG, C_BOOL);

  /** {@code id -[receiver selector:id selector:NSInteger selector:id]}. */
  public final FunctionDescriptor MSG_ID_ID_LONG_ID =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG, C_POINTER);

  /** {@code id -[receiver selector:id selector:id selector:NSInteger selector:id]}. */
  public final FunctionDescriptor MSG_ID_ID_ID_LONG_ID =
      FunctionDescriptor.of(
          C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG, C_POINTER);

  /** {@code id -[receiver selector:id selector:id selector:NSUInteger]}. */
  public final FunctionDescriptor MSG_ID_ID_ID_LONG =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG);

  /** {@code id -[receiver selector:id selector:id selector:id]}. */
  public final FunctionDescriptor MSG_ID_ID_ID_ID =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code id -[receiver selector:id selector:id selector:id selector:NSUInteger]}. */
  public final FunctionDescriptor MSG_ID_ID_ID_ID_LONG =
      FunctionDescriptor.of(
          C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG);

  /** {@code id -[receiver selector:id selector:id selector:id error:NSError**]}. */
  public final FunctionDescriptor MSG_ID_ID_ID_ID_POINTER =
      FunctionDescriptor.of(
          C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** {@code id -[receiver selector:id selector:NSInteger selector:id selector:id]}. */
  public final FunctionDescriptor MSG_ID_ID_LONG_ID_ID =
      FunctionDescriptor.of(
          C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG, C_POINTER, C_POINTER);

  /** {@code NSInteger -[receiver selector:void* selector:NSUInteger]}: {@code read:maxLength:}. */
  public final FunctionDescriptor MSG_LONG_POINTER_LONG =
      FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_POINTER, C_LONG);

  /** {@code void -[receiver selector:NSUInteger selector:id]}. */
  public final FunctionDescriptor MSG_VOID_LONG_ID =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_LONG, C_POINTER);

  /**
   * {@code id -[receiver selector:NSRect selector:NSUInteger selector:NSUInteger selector:BOOL]}.
   */
  public final FunctionDescriptor MSG_ID_RECT_LONG_LONG_BOOL =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, NSRECT, C_LONG, C_LONG, C_BOOL);

  /** {@code id -[receiver selector:NSRect selector:id]}. */
  public final FunctionDescriptor MSG_ID_RECT_ID =
      FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, NSRECT, C_POINTER);

  /** {@code void -[receiver selector:NSSize]}. */
  public final FunctionDescriptor MSG_VOID_SIZE =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, NSSIZE);

  /**
   * {@code +[NSEvent
   * otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:data1:
   * data2:]}.
   */
  public final FunctionDescriptor MSG_OTHER_EVENT =
      FunctionDescriptor.of(
          C_POINTER, C_POINTER, C_POINTER, C_LONG, NSPOINT, C_LONG, C_DOUBLE, C_LONG, C_POINTER,
          C_SHORT, C_LONG, C_LONG);

  // --- callbacks ---

  /**
   * A method without arguments: {@code -(void)method}. It receives {@code self} and {@code _cmd}.
   */
  public final FunctionDescriptor DELEGATE_0 = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER);

  /**
   * A delegate method with one argument: {@code -(void)method:(id)a}. It receives {@code self} and
   * {@code _cmd} first.
   */
  public final FunctionDescriptor DELEGATE_1 = VOID_POINTER_POINTER_POINTER;

  /**
   * A delegate method with one argument that answers {@code BOOL}, such as {@code
   * windowShouldClose:}.
   */
  public final FunctionDescriptor DELEGATE_1_BOOL =
      FunctionDescriptor.of(C_BOOL, C_POINTER, C_POINTER, C_POINTER);

  /**
   * A delegate method with one argument that answers {@code NSUInteger}, such as {@code
   * applicationShouldTerminate:}.
   */
  public final FunctionDescriptor DELEGATE_1_LONG =
      FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_POINTER);

  /** A delegate method with two arguments. */
  public final FunctionDescriptor DELEGATE_2 =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /** A delegate method with three arguments. */
  public final FunctionDescriptor DELEGATE_3 =
      FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /**
   * A delegate method with four arguments that answers an object, such as {@code
   * webView:createWebViewWithConfiguration:forNavigationAction:windowFeatures:}.
   */
  public final FunctionDescriptor DELEGATE_4_ID =
      FunctionDescriptor.of(
          C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

  /**
   * {@code void (^)(NSModalResponse)}: the completion handler of a sheet. It receives the block
   * itself first.
   */
  public final FunctionDescriptor SHEET_BLOCK = FunctionDescriptor.ofVoid(C_POINTER, C_LONG);

  /** {@code void (^)(id result, NSError* error)}. It receives the block itself first. */
  public final FunctionDescriptor COMPLETION_BLOCK = VOID_POINTER_POINTER_POINTER;

  /** {@code void (^)(NSError* error)}. It receives the block itself first. */
  public final FunctionDescriptor ERROR_BLOCK = DELEGATE_0;

  /** {@code void (^)(BOOL granted, NSError* error)}. It receives the block itself first. */
  public final FunctionDescriptor AUTHORIZATION_BLOCK =
      FunctionDescriptor.ofVoid(C_POINTER, C_BOOL, C_POINTER);

  // --- calling a block that the runtime passed in: the invoke function, with the block first ---

  /** {@code void (^)(void)}. */
  public final FunctionDescriptor CALL_BLOCK = VOID_POINTER;

  /** {@code void (^)(NSUInteger)}. */
  public final FunctionDescriptor CALL_BLOCK_LONG = FunctionDescriptor.ofVoid(C_POINTER, C_LONG);
}
