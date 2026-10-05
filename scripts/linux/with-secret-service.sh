#!/usr/bin/env bash
#
# Runs a command with a Secret Service on the session bus of the caller: GNOME Keyring, with a
# login keyring that it creates and unlocks with a password of the test session, the way a desktop
# unlocks it at login:
#
#   dbus-run-session -- scripts/linux/with-secret-service.sh ./gradlew displayTest
#
# Without a running service, or with a locked one, the tests of the secrets have nothing to store
# in. The keyring lives in a temporary home of its own, so a run never touches the keyring of the
# user, and goes with the command.
#
set -euo pipefail
keyrings=$(mktemp -d)
export XDG_DATA_HOME="$keyrings"
eval "$(printf 'lwjwae' | gnome-keyring-daemon --unlock --components=secrets)"
export GNOME_KEYRING_CONTROL
trap 'kill "$GNOME_KEYRING_PID" 2>/dev/null || true; rm -rf "$keyrings"' EXIT
"$@"
