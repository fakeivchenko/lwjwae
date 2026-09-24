#!/usr/bin/env bash
#
# Runs a command on the X display of the caller with openbox as the window manager, the way a
# desktop has one:
#
#   xvfb-run -a scripts/linux/with-window-manager.sh ./gradlew displayTest
#
# Without a window manager, a request to minimize, maximize, or keep a window on top goes nowhere,
# and the window tests of those would fail on a bare Xvfb. openbox follows EWMH and leaves the size
# and the place of a window as the client asks, so the other tests see what they see without it.
# The command starts only once openbox manages the screen, and openbox ends with the command.
#
set -euo pipefail
ready=$(mktemp -u)
openbox --startup "touch $ready" &
wm=$!
trap 'kill $wm 2>/dev/null || true; rm -f "$ready"' EXIT
for _ in $(seq 1 100); do
    [ -e "$ready" ] && break
    sleep 0.1
done
[ -e "$ready" ] || { echo "openbox did not start" >&2; exit 1; }
"$@"
