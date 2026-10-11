#!/usr/bin/env bash
#
# Runs a command on a Wayland display of its own: Weston, headless, which draws in memory, the way
# Xvfb does for X11. Only the command sees it, so a service that the session bus starts, such as
# the notification server, stays on the X display of the caller:
#
#   xvfb-run -a dbus-run-session -- scripts/linux/with-wayland.sh ./gradlew displayTest
#
# The command starts once Weston has its socket, and Weston ends with the command.
#
set -euo pipefail
runtime=$(mktemp -d)
chmod 700 "$runtime"
socket=wayland-lwjwae
XDG_RUNTIME_DIR="$runtime" weston --backend=headless --socket="$socket" --width=1600 --height=1000 \
    > "$runtime/weston.log" 2>&1 &
compositor=$!
trap 'kill $compositor 2>/dev/null || true; rm -rf "$runtime"' EXIT
for _ in $(seq 1 100); do
    [ -S "$runtime/$socket" ] && break
    sleep 0.1
done
[ -S "$runtime/$socket" ] || { cat "$runtime/weston.log" >&2; echo "weston did not start" >&2; exit 1; }
XDG_RUNTIME_DIR="$runtime" WAYLAND_DISPLAY="$socket" GDK_BACKEND=wayland "$@"
