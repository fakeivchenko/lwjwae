package dev.ivchenko.lwjwae.util;

import java.util.Locale;

/**
 * The bytes of a resource that a request asked for with a {@code Range} header, from {@code first}
 * to {@code last}, both included.
 *
 * <p>An engine plays audio and video by asking for ranges: it reads the header of the file, jumps
 * to the index, and seeks by requesting the bytes at the new position. An origin that answers every
 * request with the whole file makes the player download it once more for each of those steps, which
 * is why a video from such an origin stutters or doesn't play at all.
 *
 * <p>Only a single range is honored. A request for several ranges, or with a header that doesn't
 * parse, gets the whole resource, which HTTP allows a server to send in place of any range; no
 * engine asks for more than one range for media, and a multipart answer would cost a lot of code
 * for no player.
 *
 * @param first The offset of the first byte.
 * @param last The offset of the last byte; less than {@code first} when no byte of the resource is
 *     in the range, see {@link #isSatisfiable}.
 */
public record ByteRange(long first, long last) {
  private static final String UNIT = "bytes=";

  /**
   * The range that the {@code Range} header {@code header} asks for in a resource of {@code size}
   * bytes, with its end cut to the end of the resource, or {@code null} when the request should get
   * the whole resource: without a header, with several ranges, or with one that doesn't parse.
   */
  public static ByteRange parse(String header, long size) {
    if (header == null || !header.toLowerCase(Locale.ROOT).startsWith(UNIT)) {
      return null;
    }
    String spec = header.substring(UNIT.length()).trim();
    int dash = spec.indexOf('-');
    if (dash < 0 || spec.indexOf(',') >= 0) {
      return null;
    }
    String start = spec.substring(0, dash).trim();
    String end = spec.substring(dash + 1).trim();
    if (!ByteRange.isDigits(start) || !ByteRange.isDigits(end)) {
      return null;
    }
    if (start.isEmpty()) {
      // A suffix, bytes=-500: the last 500 bytes, all of them for a shorter resource.
      if (end.isEmpty()) {
        return null;
      }
      long suffix = ByteRange.parseOffset(end);
      return new ByteRange(suffix == 0 ? size : Math.max(0, size - suffix), size - 1);
    }
    long first = ByteRange.parseOffset(start);
    if (end.isEmpty()) {
      return new ByteRange(first, size - 1);
    }
    long last = ByteRange.parseOffset(end);
    if (last < first) {
      return null;
    }
    return new ByteRange(first, Math.min(last, size - 1));
  }

  /**
   * Whether any byte of the resource is in the range. A range that starts past the end is answered
   * with {@code 416 Range Not Satisfiable}.
   */
  public boolean isSatisfiable() {
    return this.first <= this.last;
  }

  /** The number of bytes in the range. */
  public long length() {
    return this.last - this.first + 1;
  }

  /** The {@code Content-Range} header of the answer, for a resource of {@code size} bytes. */
  public String contentRange(long size) {
    return this.isSatisfiable()
        ? "bytes %d-%d/%d".formatted(this.first, this.last, size)
        : "bytes */%d".formatted(size);
  }

  private static boolean isDigits(String text) {
    return text.chars().allMatch(character -> character >= '0' && character <= '9');
  }

  /** An offset of digits only; one past the range of a long is past the end of any resource. */
  private static long parseOffset(String digits) {
    try {
      return Long.parseLong(digits);
    } catch (NumberFormatException _) {
      return Long.MAX_VALUE;
    }
  }
}
