package dev.ivchenko.lwjwae;

/**
 * An edge or a corner of a window, the one that the user grabs to resize it.
 *
 * <p>The page names the edge in {@code window.lwjwae.window.startResize(edge)} with {@link
 * #pageName()}, which is also how a backend without a native frame learns which edge a drag starts
 * at.
 */
public enum WindowEdge {
  TOP("top"),
  BOTTOM("bottom"),
  LEFT("left"),
  RIGHT("right"),
  TOP_LEFT("top-left"),
  TOP_RIGHT("top-right"),
  BOTTOM_LEFT("bottom-left"),
  BOTTOM_RIGHT("bottom-right");

  private final String pageName;

  WindowEdge(String pageName) {
    this.pageName = pageName;
  }

  /** The name of the edge on the page, for example {@code top-left}. */
  public String pageName() {
    return this.pageName;
  }

  /** The edge of a page name, or {@code null} for a name that isn't one. */
  public static WindowEdge ofPageName(String name) {
    for (WindowEdge edge : values()) {
      if (edge.pageName.equals(name)) {
        return edge;
      }
    }
    return null;
  }
}
