package dev.ivchenko.lwjwae.glib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;

/**
 * Drags files from a window of another process onto a window of the process under test, as a user
 * does with a file manager, through the XDND protocol of X11.
 *
 * <p>The source is a small GTK 3 program in Python that offers the files as a {@code
 * text/uri-list}; {@code xdotool} presses the pointer through XTest, so the X server and the window
 * manager treat the drag like one of a person. Both tools are in the Linux image of the tests.
 */
@UtilityClass
public class XdndFileDrag {
  private final String SOURCE_TITLE = "lwjwae-drag-source";
  private final String SCRIPT =
      """
      import sys, gi
      gi.require_version('Gtk', '3.0')
      from gi.repository import Gtk, Gdk, GLib
      uris = [GLib.filename_to_uri(path) for path in sys.argv[1:]]
      window = Gtk.Window(title='lwjwae-drag-source')
      window.set_default_size(160, 80)
      window.move(0, 0)
      box = Gtk.EventBox()
      box.add(Gtk.Label(label='drag me'))
      box.drag_source_set(Gdk.ModifierType.BUTTON1_MASK, [Gtk.TargetEntry.new('text/uri-list', 0, 0)], Gdk.DragAction.COPY)
      box.connect('drag-data-get', lambda widget, context, data, info, time: data.set_uris(uris))
      window.add(box)
      window.connect('destroy', Gtk.main_quit)
      window.show_all()
      GLib.timeout_add(500, lambda: (print('ready', flush=True), False)[1])
      Gtk.main()
      """;

  /** Whether the session is X11, where XDND and XTest work; Wayland lets no client press. */
  public boolean isX11Session() {
    return System.getenv("DISPLAY") != null
        && (System.getenv("WAYLAND_DISPLAY") == null || "x11".equals(System.getenv("GDK_BACKEND")));
  }

  /**
   * Drags {@code files} onto the window titled {@code targetTitle}, and returns once the pointer is
   * released over it.
   *
   * @return Whether the drag could be made: the tools exist, and the source window came up.
   */
  public boolean dragOnto(String targetTitle, List<Path> files) throws Exception {
    Path script = Files.createTempFile("lwjwae-drag-source", ".py");
    Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
    List<String> command = new ArrayList<>(List.of("python3", script.toString()));
    files.forEach(file -> command.add(file.toString()));
    Process source;
    try {
      source = new ProcessBuilder(command).redirectErrorStream(true).start();
    } catch (IOException _) {
      return false;
    }
    try {
      BufferedReader out =
          new BufferedReader(
              new InputStreamReader(source.getInputStream(), StandardCharsets.UTF_8));
      // GTK warns on the way, about a bus of accessibility that the image doesn't have.
      String line = out.readLine();
      while (line != null && !"ready".equals(line)) {
        line = out.readLine();
      }
      if (line == null) {
        return false;
      }
      int[] from = XdndFileDrag.center(SOURCE_TITLE);
      int[] to = XdndFileDrag.center(targetTitle);
      if (from == null || to == null) {
        return false;
      }
      return XdndFileDrag.run(
          List.of(
              "xdotool",
              "mousemove",
              String.valueOf(from[0]),
              String.valueOf(from[1]),
              "sleep",
              "0.2",
              "mousedown",
              "1",
              "sleep",
              "0.3",
              "mousemove_relative",
              "--sync",
              "12",
              "12",
              "sleep",
              "0.3",
              "mousemove_relative",
              "--sync",
              "12",
              "12",
              "sleep",
              "0.3",
              "mousemove",
              String.valueOf(to[0] - 20),
              String.valueOf(to[1] - 20),
              "sleep",
              "0.3",
              "mousemove",
              String.valueOf(to[0]),
              String.valueOf(to[1]),
              "sleep",
              "0.3",
              "mousemove_relative",
              "--sync",
              "2",
              "2",
              "sleep",
              "0.6",
              "mouseup",
              "1"));
    } finally {
      source.destroy();
      Files.deleteIfExists(script);
    }
  }

  /**
   * The middle of the largest window titled {@code title}, in screen coordinates, or {@code null}.
   * GTK has small windows of that name as well, such as the one that groups the windows of a
   * program, and the one that the user sees is the largest.
   */
  private int[] center(String title) throws Exception {
    String ids = XdndFileDrag.output(List.of("xdotool", "search", "--sync", "--name", title));
    if (ids == null || ids.isBlank()) {
      return null;
    }
    int[] best = null;
    long bestArea = 0;
    for (String id : ids.split("\\R")) {
      String geometry =
          XdndFileDrag.output(List.of("xdotool", "getwindowgeometry", "--shell", id.strip()));
      if (geometry == null) {
        continue;
      }
      int x = 0;
      int y = 0;
      int width = 0;
      int height = 0;
      for (String line : geometry.split("\\R")) {
        String[] pair = line.split("=");
        if (pair.length != 2) {
          continue;
        }
        switch (pair[0]) {
          case "X" -> x = Integer.parseInt(pair[1].strip());
          case "Y" -> y = Integer.parseInt(pair[1].strip());
          case "WIDTH" -> width = Integer.parseInt(pair[1].strip());
          case "HEIGHT" -> height = Integer.parseInt(pair[1].strip());
          default -> {}
        }
      }
      if ((long) width * height > bestArea) {
        bestArea = (long) width * height;
        best = new int[] {x + width / 2, y + height / 2};
      }
    }
    return best;
  }

  private String output(List<String> command) throws Exception {
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0
          ? text.strip()
          : null;
    } catch (IOException _) {
      return null;
    }
  }

  private boolean run(List<String> command) throws Exception {
    return XdndFileDrag.output(command) != null;
  }
}
