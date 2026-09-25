package dev.ivchenko.lwjwae.windows.binding;

/**
 * What {@code GetMonitorInfoW} tells about a monitor.
 *
 * @param monitor {@code {left, top, right, bottom}} of the whole monitor.
 * @param work {@code {left, top, right, bottom}} of the monitor minus the taskbar.
 * @param primary Whether it's the primary monitor.
 * @param device The name of the display device, such as {@code \\.\DISPLAY1}.
 */
public record MonitorInfo(int[] monitor, int[] work, boolean primary, String device) {}
