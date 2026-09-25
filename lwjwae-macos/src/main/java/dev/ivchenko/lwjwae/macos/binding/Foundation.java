package dev.ivchenko.lwjwae.macos.binding;

import dev.ivchenko.lwjwae.foreign.NativeLibraries;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.List;
import lombok.experimental.UtilityClass;

/** Foundation: strings, URLs, data, and errors, converted to and from their Java shapes. */
@UtilityClass
public class Foundation {
  static {
    // Loaded for the classes it registers; nothing is looked up by symbol.
    SymbolLookup _ =
        NativeLibraries.load("/System/Library/Frameworks/Foundation.framework/Foundation");
  }

  /** An autoreleased {@code NSString}; {@code null} becomes {@code nil}. */
  public MemorySegment string(String text) {
    if (text == null) {
      return MemorySegment.NULL;
    }
    try (Arena arena = Arena.ofConfined()) {
      return ObjC.send(ObjC.cls("NSString"), "stringWithUTF8String:", arena.allocateFrom(text));
    }
  }

  /** The Java string of an {@code NSString}. {@code nil} becomes {@code null}. */
  public String string(MemorySegment nsString) {
    if (ObjC.isNull(nsString)) {
      return null;
    }
    return NativeLibraries.string(ObjC.send(nsString, "UTF8String"));
  }

  /** An autoreleased {@code NSURL}. */
  public MemorySegment url(String url) {
    return ObjC.send(ObjC.cls("NSURL"), "URLWithString:", Foundation.string(url));
  }

  /** {@code -[NSURL absoluteString]}. */
  public String urlString(MemorySegment nsUrl) {
    return Foundation.string(ObjC.send(nsUrl, "absoluteString"));
  }

  /** An autoreleased {@code NSData} holding a copy of {@code bytes}. */
  public MemorySegment data(byte[] bytes) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
      return ObjC.send(ObjC.cls("NSData"), "dataWithBytes:length:", buffer, bytes.length);
    }
  }

  /** An autoreleased {@code NSError} in the domain of the library. */
  public MemorySegment error(long code, String description) {
    MemorySegment key = Foundation.string("NSLocalizedDescription");
    MemorySegment userInfo =
        ObjC.send(
            ObjC.cls("NSDictionary"),
            "dictionaryWithObject:forKey:",
            Foundation.string(description),
            key);
    return ObjC.send(
        ObjC.cls("NSError"),
        "errorWithDomain:code:userInfo:",
        Foundation.string("dev.ivchenko.lwjwae"),
        code,
        userInfo);
  }

  /** An autoreleased {@code NSArray} of {@code objects}, in order. */
  public MemorySegment array(List<MemorySegment> objects) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate(Signatures.C_POINTER, Math.max(1, objects.size()));
      for (int index = 0; index < objects.size(); index++) {
        buffer.setAtIndex(Signatures.C_POINTER, index, objects.get(index));
      }
      return ObjC.send(ObjC.cls("NSArray"), "arrayWithObjects:count:", buffer, objects.size());
    }
  }

  /** {@code -[NSError localizedDescription]}. */
  public String errorDescription(MemorySegment error) {
    return Foundation.string(ObjC.send(error, "localizedDescription"));
  }

  /** The URL an {@code NSError} from WebKit was loading, or {@code null}. */
  public String errorFailingUrl(MemorySegment error) {
    MemorySegment userInfo = ObjC.send(error, "userInfo");
    String url =
        Foundation.string(
            ObjC.send(userInfo, "objectForKey:", Foundation.string("NSErrorFailingURLStringKey")));
    if (url != null) {
      return url;
    }
    MemorySegment nsUrl =
        ObjC.send(userInfo, "objectForKey:", Foundation.string("NSErrorFailingURLKey"));
    return ObjC.isNull(nsUrl) ? null : Foundation.urlString(nsUrl);
  }

  /**
   * The {@code NSRect} behind a {@code frame}-like property, read through key-value coding.
   *
   * <p>KVC boxes the struct into an {@code NSValue}, and {@code getValue:size:} copies it out.
   * There's no struct return, so there's no {@code objc_msgSend_stret}, which exists only on
   * x86_64.
   */
  public double[] rect(MemorySegment object, String key) {
    MemorySegment value = ObjC.send(object, "valueForKey:", Foundation.string(key));
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rect = arena.allocate(Signatures.NSRECT);
      ObjC.sendVoid(value, "getValue:size:", rect, Signatures.NSRECT.byteSize());
      return new double[] {
        rect.get(Signatures.C_DOUBLE, 0), rect.get(Signatures.C_DOUBLE, 8),
        rect.get(Signatures.C_DOUBLE, 16), rect.get(Signatures.C_DOUBLE, 24)
      };
    }
  }

  /** An {@code NSRect} allocated from {@code arena}. */
  public MemorySegment rect(Arena arena, double x, double y, double width, double height) {
    MemorySegment rect = arena.allocate(Signatures.NSRECT);
    rect.set(Signatures.C_DOUBLE, 0, x);
    rect.set(Signatures.C_DOUBLE, 8, y);
    rect.set(Signatures.C_DOUBLE, 16, width);
    rect.set(Signatures.C_DOUBLE, 24, height);
    return rect;
  }

  /** An {@code NSSize} allocated from {@code arena}. */
  public MemorySegment size(Arena arena, double width, double height) {
    MemorySegment size = arena.allocate(Signatures.NSSIZE);
    size.set(Signatures.C_DOUBLE, 0, width);
    size.set(Signatures.C_DOUBLE, 8, height);
    return size;
  }

  /** An {@code NSPoint} allocated from {@code arena}. */
  public MemorySegment point(Arena arena, double x, double y) {
    MemorySegment point = arena.allocate(Signatures.NSPOINT);
    point.set(Signatures.C_DOUBLE, 0, x);
    point.set(Signatures.C_DOUBLE, 8, y);
    return point;
  }

  /** {@code -[NSObject retain]}. Returns the receiver. */
  public MemorySegment retain(MemorySegment object) {
    return ObjC.send(object, "retain");
  }

  /** {@code -[NSObject release]}; {@code nil} is ignored. */
  public void release(MemorySegment object) {
    if (!ObjC.isNull(object)) {
      ObjC.sendVoid(object, "release");
    }
  }

  /** {@code CFBundleVersion} of the bundle that defines {@code cls}, or {@code null}. */
  public String bundleVersion(MemorySegment cls) {
    MemorySegment bundle = ObjC.send(ObjC.cls("NSBundle"), "bundleForClass:", cls);
    return Foundation.string(
        ObjC.send(bundle, "objectForInfoDictionaryKey:", Foundation.string("CFBundleVersion")));
  }

  /**
   * {@code -[NSProcessInfo operatingSystemVersionString]}, for example {@code Version 14.6.1 (Build
   * 23G93)}.
   */
  public String operatingSystemVersion() {
    return Foundation.string(
        ObjC.send(
            ObjC.send(ObjC.cls("NSProcessInfo"), "processInfo"), "operatingSystemVersionString"));
  }

  /**
   * {@code -[NSProcessInfo processName]}: the name of the executable, {@code java} under the
   * launcher.
   */
  public String processName() {
    return Foundation.string(
        ObjC.send(ObjC.send(ObjC.cls("NSProcessInfo"), "processInfo"), "processName"));
  }
}
