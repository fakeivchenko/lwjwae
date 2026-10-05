package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.util.PlatformUtil;
import dev.ivchenko.lwjwae.windows.binding.User32;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class WindowsWindowTest extends WindowContractTest {
  private static final int DOWN = 0x28;
  private static final int RETURN = 0x0D;

  @Override
  protected boolean isThisPlatform() {
    return PlatformUtil.isWindows();
  }

  @Override
  protected Class<? extends Application> expectedApplicationType() {
    return WindowsApplication.class;
  }

  @Override
  protected Class<? extends Window> expectedWindowType() {
    return WindowsWindow.class;
  }

  @Override
  protected boolean pressKeys(Shortcut shortcut) {
    Keyboard.press(shortcut);
    return true;
  }

  /** A menu of Windows opens with nothing selected: the arrow selects the first entry. */
  @Override
  protected boolean pickFirstEntryOfOpenMenu() {
    Keyboard.tap(DOWN, RETURN);
    return true;
  }

  /** The user switches the "app mode" in the settings, which is a value of the registry. */
  @Override
  protected boolean switchSystemTheme(SystemTheme theme) throws Exception {
    Process process =
        new ProcessBuilder(
                "reg",
                "add",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v",
                "AppsUseLightTheme",
                "/t",
                "REG_DWORD",
                "/d",
                theme == SystemTheme.DARK ? "0" : "1",
                "/f")
            .redirectErrorStream(true)
            .start();
    process.getInputStream().readAllBytes();
    return process.waitFor() == 0;
  }

  /**
   * Drags the files from a form of PowerShell, which does what a file manager does: {@code
   * DoDragDrop} with a {@code FileDrop}. A timer inside the drag loop moves the pointer onto the
   * window and lets go; the pointer is the one of the interactive session, so the drag is real.
   *
   * <p>The form sits in a corner of the screen that the window doesn't cover, and the script logs
   * what it saw into the output of the test: where the press landed, where the drop landed, and how
   * the drag ended, so a failure tells a drag that never started from a drop that went elsewhere.
   */
  @Override
  protected boolean dropFiles(String title, List<Path> files) throws Exception {
    Path script = Files.createTempFile("lwjwae-drag", ".ps1");
    Path log = Files.createTempFile("lwjwae-drag", ".log");
    String list =
        files.stream()
            .map(file -> "'" + file.toString().replace("'", "''") + "'")
            .collect(Collectors.joining(","));
    Files.writeString(
        script,
        """
        Add-Type -AssemblyName System.Windows.Forms, System.Drawing
        Add-Type -Namespace Lwjwae -Name Mouse -MemberDefinition '[DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y); [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, int extra); [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern System.IntPtr FindWindowW(string cls, string title); [DllImport("user32.dll")] public static extern bool GetWindowRect(System.IntPtr hwnd, out RECT rect); [DllImport("user32.dll")] public static extern System.IntPtr WindowFromPoint(POINT point); [DllImport("user32.dll")] public static extern System.IntPtr GetAncestor(System.IntPtr hwnd, uint flags); [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowTextW(System.IntPtr hwnd, System.Text.StringBuilder text, int length); [DllImport("user32.dll")] public static extern System.IntPtr GetForegroundWindow(); public struct POINT { public int X, Y; } public struct RECT { public int Left, Top, Right, Bottom; }'
        $log = '%s'
        [Lwjwae.Mouse]::SetProcessDPIAware() | Out-Null
        $hwnd = [Lwjwae.Mouse]::FindWindowW([NullString]::Value, '%s')
        $rect = New-Object Lwjwae.Mouse+RECT
        [Lwjwae.Mouse]::GetWindowRect($hwnd, [ref]$rect) | Out-Null
        $targetX = [int](($rect.Left + $rect.Right) / 2)
        $targetY = [int](($rect.Top + $rect.Bottom) / 2)
        function Title($window) {
          $text = New-Object System.Text.StringBuilder 256
          [Lwjwae.Mouse]::GetWindowTextW([Lwjwae.Mouse]::GetAncestor($window, 2), $text, 256) | Out-Null
          $text.ToString()
        }
        Add-Content -Path $log -Value ("target $hwnd at $targetX,$targetY")
        # A menu that an earlier test left open holds the pointer; Escape closes it.
        [System.Windows.Forms.SendKeys]::SendWait('{ESC}')
        $files = [string[]]@(%s)
        $form = New-Object System.Windows.Forms.Form
        $form.Text = 'lwjwae-drag-source'
        $form.StartPosition = 'Manual'
        # A corner of the screen clear of the target, its invisible resize edges included: a press
        # on one of those would start a resize of the target rather than the drag. The top ones
        # first: a notification of an earlier test covers the bottom right for a while.
        $area = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
        $corners = @(
          @(($area.Left + 20), ($area.Top + 20)), @(($area.Right - 260), ($area.Top + 20)),
          @(($area.Left + 20), ($area.Bottom - 160)), @(($area.Right - 260), ($area.Bottom - 160)))
        $corner = $corners | Where-Object {
          $_[0] + 240 -lt $rect.Left -or $_[0] -gt $rect.Right -or $_[1] + 140 -lt $rect.Top -or $_[1] -gt $rect.Bottom
        } | Select-Object -First 1
        if ($corner -eq $null) { $corner = $corners[0] }
        $form.Location = New-Object System.Drawing.Point($corner[0], $corner[1])
        $form.Size = New-Object System.Drawing.Size(220, 120)
        $startX = $corner[0] + 110
        $startY = $corner[1] + 75
        $form.TopMost = $true
        $label = New-Object System.Windows.Forms.Label
        $label.Text = 'drag me'
        $label.Dock = 'Fill'
        $form.Controls.Add($label)
        $step = 0
        $timer = New-Object System.Windows.Forms.Timer
        $timer.Interval = 300
        $timer.Add_Tick({
          $script:step++
          switch ($script:step) {
            1 { [Lwjwae.Mouse]::SetCursorPos($startX + 20, $startY + 10) | Out-Null }
            2 { [Lwjwae.Mouse]::SetCursorPos($startX + 40, $startY + 20) | Out-Null }
            3 { [Lwjwae.Mouse]::SetCursorPos($targetX - 30, $targetY - 30) | Out-Null }
            4 { [Lwjwae.Mouse]::SetCursorPos($targetX, $targetY) | Out-Null }
            5 { [Lwjwae.Mouse]::SetCursorPos($targetX + 2, $targetY + 2) | Out-Null }
            7 {
              $point = New-Object Lwjwae.Mouse+POINT
              $point.X = $targetX; $point.Y = $targetY
              Add-Content -Path $log -Value ("drop over '" + (Title ([Lwjwae.Mouse]::WindowFromPoint($point))) + "'")
              [Lwjwae.Mouse]::mouse_event(4, 0, 0, 0, 0); $timer.Stop()
            }
          }
        })
        $label.Add_MouseDown({
          $data = New-Object System.Windows.Forms.DataObject
          $data.SetData([System.Windows.Forms.DataFormats]::FileDrop, $files)
          Add-Content -Path $log -Value ("drag starts, foreground '" + (Title ([Lwjwae.Mouse]::GetForegroundWindow())) + "'")
          $timer.Start()
          $effect = $label.DoDragDrop($data, [System.Windows.Forms.DragDropEffects]::Copy)
          Add-Content -Path $log -Value ("drag ends: $effect")
          $form.Close()
        })
        $form.Add_Shown({
          [Lwjwae.Mouse]::SetCursorPos($startX, $startY) | Out-Null
          $point = New-Object Lwjwae.Mouse+POINT
          $point.X = $startX; $point.Y = $startY
          # Something that pops up over the form, such as a notification, would take the press.
          for ($try = 0; $try -lt 20; $try++) {
            Start-Sleep -Milliseconds 300
            if ((Title ([Lwjwae.Mouse]::WindowFromPoint($point))) -eq 'lwjwae-drag-source') { break }
            $form.Activate()
          }
          Add-Content -Path $log -Value ("press over '" + (Title ([Lwjwae.Mouse]::WindowFromPoint($point))) + "'")
          [Lwjwae.Mouse]::mouse_event(2, 0, 0, 0, 0)
        })
        $watchdog = New-Object System.Windows.Forms.Timer
        $watchdog.Interval = 15000
        $watchdog.Add_Tick({
          Add-Content -Path $log -Value ("watchdog: no drag ended")
          [Lwjwae.Mouse]::mouse_event(4, 0, 0, 0, 0)
          $form.Close()
        })
        $watchdog.Start()
        [System.Windows.Forms.Application]::Run($form)
        """
            .formatted(log.toString().replace("'", "''"), title.replace("'", "''"), list),
        StandardCharsets.UTF_8);
    Process process = null;
    try {
      process =
          new ProcessBuilder(
                  "powershell",
                  "-NoProfile",
                  "-ExecutionPolicy",
                  "Bypass",
                  "-File",
                  script.toString())
              .redirectErrorStream(true)
              .start();
      Process running = process;
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  running.getInputStream().readAllBytes();
                } catch (java.io.IOException _) {
                  // The process was stopped.
                }
              });
      boolean ended = process.waitFor(30, TimeUnit.SECONDS);
      // What the drag source saw, in the output of the test, which tells a drag that never started
      // from a drop that landed elsewhere.
      System.out.println("drag source: " + String.join(" | ", Files.readAllLines(log)));
      return ended && process.exitValue() == 0;
    } finally {
      if (process != null) {
        // A drag that never ended holds the pointer of the session.
        process.destroyForcibly();
      }
      Files.deleteIfExists(script);
      Files.deleteIfExists(log);
    }
  }

  /** {@code GetWindow} with {@code GW_OWNER}. */
  @Override
  protected Boolean isOwnedBy(Window child, Window parent) {
    return WindowsDispatcher.instance()
        .call(
            () ->
                User32.owner(((WindowsWindow) child).handle())
                    .equals(((WindowsWindow) parent).handle()));
  }

  /** A disabled window takes no input, which is what a modal window does to its owner. */
  @Override
  protected Boolean isBlockedByModal(Window parent) {
    return WindowsDispatcher.instance()
        .call(() -> !User32.isEnabled(((WindowsWindow) parent).handle()));
  }
}
