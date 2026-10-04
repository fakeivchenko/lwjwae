package dev.ivchenko.lwjwae.windows;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.shortcut.Shortcut;
import dev.ivchenko.lwjwae.testing.contract.WindowContractTest;
import dev.ivchenko.lwjwae.theme.SystemTheme;
import dev.ivchenko.lwjwae.util.PlatformUtil;
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
   */
  @Override
  protected boolean dropFiles(String title, List<Path> files) throws Exception {
    Path script = Files.createTempFile("lwjwae-drag", ".ps1");
    String list =
        files.stream()
            .map(file -> "'" + file.toString().replace("'", "''") + "'")
            .collect(Collectors.joining(","));
    Files.writeString(
        script,
        """
        Add-Type -AssemblyName System.Windows.Forms, System.Drawing
        Add-Type -Namespace Lwjwae -Name Mouse -MemberDefinition '[DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y); [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, int extra); [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern System.IntPtr FindWindowW(string cls, string title); [DllImport("user32.dll")] public static extern bool GetWindowRect(System.IntPtr hwnd, out RECT rect); public struct RECT { public int Left, Top, Right, Bottom; }'
        [Lwjwae.Mouse]::SetProcessDPIAware() | Out-Null
        $hwnd = [Lwjwae.Mouse]::FindWindowW([NullString]::Value, '%s')
        $rect = New-Object Lwjwae.Mouse+RECT
        [Lwjwae.Mouse]::GetWindowRect($hwnd, [ref]$rect) | Out-Null
        $targetX = [int](($rect.Left + $rect.Right) / 2)
        $targetY = [int](($rect.Top + $rect.Bottom) / 2)
        # A menu that an earlier test left open holds the pointer; Escape closes it.
        [System.Windows.Forms.SendKeys]::SendWait('{ESC}')
        $files = [string[]]@(%s)
        $form = New-Object System.Windows.Forms.Form
        $form.Text = 'lwjwae-drag-source'
        $form.StartPosition = 'Manual'
        $form.Location = New-Object System.Drawing.Point(700, 500)
        $form.Size = New-Object System.Drawing.Size(220, 120)
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
            1 { [Lwjwae.Mouse]::SetCursorPos(760, 540) | Out-Null }
            2 { [Lwjwae.Mouse]::SetCursorPos(780, 560) | Out-Null }
            3 { [Lwjwae.Mouse]::SetCursorPos($targetX - 30, $targetY - 30) | Out-Null }
            4 { [Lwjwae.Mouse]::SetCursorPos($targetX, $targetY) | Out-Null }
            5 { [Lwjwae.Mouse]::SetCursorPos($targetX + 2, $targetY + 2) | Out-Null }
            7 { [Lwjwae.Mouse]::mouse_event(4, 0, 0, 0, 0); $timer.Stop() }
          }
        })
        $label.Add_MouseDown({
          $data = New-Object System.Windows.Forms.DataObject
          $data.SetData([System.Windows.Forms.DataFormats]::FileDrop, $files)
          $timer.Start()
          $label.DoDragDrop($data, [System.Windows.Forms.DragDropEffects]::Copy) | Out-Null
          $form.Close()
        })
        $form.Add_Shown({
          [Lwjwae.Mouse]::SetCursorPos(740, 540) | Out-Null
          Start-Sleep -Milliseconds 400
          [Lwjwae.Mouse]::mouse_event(2, 0, 0, 0, 0)
        })
        $watchdog = New-Object System.Windows.Forms.Timer
        $watchdog.Interval = 15000
        $watchdog.Add_Tick({
          [Lwjwae.Mouse]::mouse_event(4, 0, 0, 0, 0)
          $form.Close()
        })
        $watchdog.Start()
        [System.Windows.Forms.Application]::Run($form)
        """
            .formatted(title.replace("'", "''"), list),
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
      return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
    } finally {
      if (process != null) {
        // A drag that never ended holds the pointer of the session.
        process.destroyForcibly();
      }
      Files.deleteIfExists(script);
    }
  }
}
