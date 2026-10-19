#!/usr/bin/env bash
#
# Runs a command on a Wayland display of its own: labwc, the compositor that the Wayland session of
# Xfce runs on, headless, which draws in memory, the way Xvfb does for X11. Only the command sees
# it, so a service that the session bus starts, such as the notification server, stays on the X
# display of the caller:
#
#   xvfb-run -a dbus-run-session -- scripts/linux/with-wayland.sh ./gradlew displayTest
#
# The command starts once labwc has its socket, and labwc ends with the command.
#
set -euo pipefail
runtime=$(mktemp -d)
chmod 700 "$runtime"
trap 'kill $compositor 2>/dev/null || true; rm -rf "$runtime"' EXIT
XDG_RUNTIME_DIR="$runtime" WLR_BACKENDS=headless WLR_RENDERER=pixman WLR_LIBINPUT_NO_DEVICES=1 \
    labwc -s "sh -c 'echo \$WAYLAND_DISPLAY > $runtime/display'" > "$runtime/labwc.log" 2>&1 &
compositor=$!
for _ in $(seq 1 100); do
    [ -s "$runtime/display" ] && break
    sleep 0.1
done
[ -s "$runtime/display" ] || { cat "$runtime/labwc.log" >&2; echo "labwc did not start" >&2; exit 1; }
XDG_RUNTIME_DIR="$runtime" WAYLAND_DISPLAY="$(cat "$runtime/display")" GDK_BACKEND=wayland "$@"
