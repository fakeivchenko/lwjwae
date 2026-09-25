package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.util.ResourceUtil;
import lombok.Builder;

/**
 * What a window starts with.
 *
 * <p>Every component has a usable default, which the canonical constructor applies, so a caller
 * only states what it needs:
 *
 * <pre>{@code
 * WindowParameters.builder().title("Docs").size(1280, 800).build()
 * }</pre>
 *
 * <p>Platforms:
 *
 * <ul>
 *   <li>Windows: {@code decorated(false)} keeps the side and bottom resize edges outside the page
 *       and makes the top edge a strip over it. {@code closable(false)} grays out the close button
 *       and takes {@code Alt+F4} away. {@code alwaysOnTop} works from the background too, which
 *       {@link Window#alwaysOnTop(boolean)} doesn't.
 *   <li>macOS: Every window opens centered, unless it has a position. {@code decorated(false)}
 *       keeps the rounded corners, the shadow, and the resize edges; the title bar goes transparent
 *       and its buttons hidden. {@code maximizable(false)} grays out the green button, which also
 *       enters full screen.
 *   <li>Linux, GTK 3: X11: as described. Wayland: {@code position} and {@code centered} are up to
 *       the compositor. A window without its minimize or maximize button gets the title bar of GTK,
 *       with the layout of the desktop minus those buttons.
 *   <li>Linux, GTK 4: {@code position} and {@code centered} do nothing, on X11 as on Wayland, and
 *       neither do {@code maximumSize} and {@code alwaysOnTop}. The buttons as on GTK 3.
 * </ul>
 *
 * @param title The window title. Default: {@code "Application"}.
 * @param size The initial size of the content area. A dimension that isn't positive takes the
 *     default. Default: {@code 1024} by {@code 768}.
 * @param position Where the frame of the window opens on the screen, in the units of the platform,
 *     or {@code null} to let the window manager choose. Wayland ignores it: a client can't place
 *     its window there.
 * @param centered Whether the window opens in the middle of the screen. Wins over {@code position}.
 *     Default: {@code false}, except on macOS, where every window opens centered.
 * @param url The URL to load after the window exists, or {@code null} to leave the window blank.
 *     This is a convenience for simple cases. {@link Application#open} navigates before it returns,
 *     so to observe a load from its first event, leave this value unset, register the listener, and
 *     call {@link Window#navigate} yourself.
 * @param resource The classpath resource to load after the window exists, the way {@link
 *     Window#loadResource} does, or {@code null}. Wins over {@code url}.
 * @param closeAction What the window does when the user closes it. Default: {@link
 *     CloseAction#CLOSE}. {@link Window#closeAction(CloseAction)} changes it later.
 * @param minimumSize The smallest size of the content area, see {@link
 *     Window#minimumSize(WindowSize)}. Default: {@link WindowSize#NONE}.
 * @param maximumSize The largest size of the content area, see {@link
 *     Window#maximumSize(WindowSize)}. Default: {@link WindowSize#NONE}.
 * @param alwaysOnTop Whether the window stays above other windows, see {@link
 *     Window#alwaysOnTop(boolean)}. Default: {@code false}.
 * @param stateKey The name under which the window remembers its size, its place, and whether it was
 *     maximized, from one run of the application to the next, in {@link
 *     ApplicationParameters#dataDirectory()}. What it remembers wins over the size and the position
 *     here. Default: none, and the window opens as these parameters say every time.
 * @param decorated Whether the window has a title bar. Without one, the page draws its own and
 *     marks where the user can grab the window with {@code data-lwjwae-drag}; the window keeps its
 *     shadow, its corners, and its resize edges where the platform has them. Default: {@code true}.
 * @param closable Whether the user can close the window: its close button, and the shortcut and the
 *     menus of the desktop. {@link Window#close()} and {@code window.lwjwae.close()} still close
 *     it. Default: {@code true}.
 * @param minimizable Whether the title bar has a minimize button. {@link Window#minimize()} works
 *     either way. Default: {@code true}.
 * @param maximizable Whether the title bar has a maximize button, and a double click on it or on a
 *     drag region of the page maximizes the window. {@link Window#maximize()} works either way.
 *     Default: {@code true}.
 */
@Builder(toBuilder = true)
public record WindowParameters(
    String title,
    WindowSize size,
    WindowPosition position,
    boolean centered,
    String url,
    String resource,
    CloseAction closeAction,
    WindowSize minimumSize,
    WindowSize maximumSize,
    boolean alwaysOnTop,
    String stateKey,
    Boolean decorated,
    Boolean closable,
    Boolean minimizable,
    Boolean maximizable) {
  private static final String DEFAULT_TITLE = "Application";
  private static final WindowSize DEFAULT_SIZE = new WindowSize(1024, 768);

  public WindowParameters {
    if (closeAction == null) {
      closeAction = CloseAction.CLOSE;
    }
    if (minimumSize == null) {
      minimumSize = WindowSize.NONE;
    }
    if (maximumSize == null) {
      maximumSize = WindowSize.NONE;
    }
    if (title == null || title.isBlank()) {
      title = DEFAULT_TITLE;
    }
    if (size == null) {
      size = DEFAULT_SIZE;
    }
    if (size.width() <= 0 || size.height() <= 0) {
      size =
          new WindowSize(
              size.width() > 0 ? size.width() : DEFAULT_SIZE.width(),
              size.height() > 0 ? size.height() : DEFAULT_SIZE.height());
    }
    if (url != null && url.isBlank()) {
      url = null;
    }
    if (resource != null && resource.isBlank()) {
      resource = null;
    }
    if (stateKey != null && stateKey.isBlank()) {
      stateKey = null;
    }
    if (decorated == null) {
      decorated = true;
    }
    if (closable == null) {
      closable = true;
    }
    if (minimizable == null) {
      minimizable = true;
    }
    if (maximizable == null) {
      maximizable = true;
    }
  }

  /** Whether the window opens at {@link #position()} rather than where the platform puts it. */
  public boolean hasPosition() {
    return this.position != null;
  }

  /**
   * Creates a window with every default: 1024 by 768 pixels, blank, and titled {@code
   * "Application"}.
   */
  public static WindowParameters createDefault() {
    return builder().build();
  }

  /** A window titled {@code title} of {@code width} by {@code height}, blank. */
  public static WindowParameters of(String title, int width, int height) {
    return WindowParameters.builder().title(title).size(width, height).build();
  }

  /**
   * A window titled {@code title} that loads {@code target}: a URL, such as {@code
   * https://example.com}, or a file of the application, such as {@code app/index.html}, as {@link
   * Window#load} tells them apart.
   */
  public static WindowParameters of(String title, String target) {
    WindowParametersBuilder builder = WindowParameters.builder().title(title);
    return (ResourceUtil.isUrl(target) ? builder.url(target) : builder.resource(target)).build();
  }

  /**
   * The builder, with the sizes and the position also as two numbers. Lombok leaves out a method
   * whose name is already here, so the ones that take a model are here too.
   */
  public static class WindowParametersBuilder {
    /** The initial size of the content area, see {@link WindowParameters#size()}. */
    public WindowParametersBuilder size(WindowSize size) {
      this.size = size;
      return this;
    }

    /** The same as {@link #size(WindowSize)}. */
    public WindowParametersBuilder size(int width, int height) {
      return this.size(new WindowSize(width, height));
    }

    /** Where the window opens, see {@link WindowParameters#position()}. */
    public WindowParametersBuilder position(WindowPosition position) {
      this.position = position;
      return this;
    }

    /** The same as {@link #position(WindowPosition)}. */
    public WindowParametersBuilder position(int x, int y) {
      return this.position(new WindowPosition(x, y));
    }

    /** The smallest size of the content area, see {@link WindowParameters#minimumSize()}. */
    public WindowParametersBuilder minimumSize(WindowSize minimumSize) {
      this.minimumSize = minimumSize;
      return this;
    }

    /** The same as {@link #minimumSize(WindowSize)}. */
    public WindowParametersBuilder minimumSize(int width, int height) {
      return this.minimumSize(new WindowSize(width, height));
    }

    /** The largest size of the content area, see {@link WindowParameters#maximumSize()}. */
    public WindowParametersBuilder maximumSize(WindowSize maximumSize) {
      this.maximumSize = maximumSize;
      return this;
    }

    /** The same as {@link #maximumSize(WindowSize)}. */
    public WindowParametersBuilder maximumSize(int width, int height) {
      return this.maximumSize(new WindowSize(width, height));
    }
  }
}
