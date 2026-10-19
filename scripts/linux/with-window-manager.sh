#!/usr/bin/env bash
#
# Runs a command on the X display of the caller with xfwm4, the window manager of Xfce, the way a
# desktop has one:
#
#   xvfb-run -a scripts/linux/with-window-manager.sh ./gradlew displayTest
#
# Without a window manager, a request to minimize, maximize, or keep a window on top goes nowhere,
# and the window tests of those would fail on a bare Xvfb. xfwm4 follows EWMH and composites the
# screen, as on a desktop of Xfce, so a transparent window shows what is behind it. The command
# starts only once xfwm4 manages the screen, and xfwm4 ends with the command.
#
set -euo pipefail
xfwm4 --compositor=on --vblank=off --sm-client-disable > /dev/null 2>&1 &
wm=$!
trap 'kill $wm 2>/dev/null || true' EXIT
for _ in $(seq 1 100); do
    xprop -root _NET_SUPPORTING_WM_CHECK 2>/dev/null | grep -q 'window id' && break
    sleep 0.1
done
xprop -root _NET_SUPPORTING_WM_CHECK 2>/dev/null | grep -q 'window id' \
    || { echo "xfwm4 did not start" >&2; exit 1; }
"$@"
