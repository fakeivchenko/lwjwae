#!/usr/bin/env bash
#
# Runs Gradle tasks in the Linux CI environment of Dockerfile.test: a virtual X display (Xvfb) with
# openbox as the window manager, a private session bus with dunst as the notification server, and
# software rendering, the way the Linux job on GitHub runs them.
#
#   scripts/linux/test-in-docker.sh                                  # every display test
#   scripts/linux/test-in-docker.sh :lwjwae-gtk4:displayTest --tests '*Bridge*'
#
# The working tree is copied into the container, so the build writes nothing into it; the reports
# and screenshots come back to build/docker/. Gradle's caches live in a named volume between runs.
# seccomp and the system paths stay unconfined because the web processes of WebKitGTK 6.0 run in a
# bubblewrap sandbox, which needs user namespaces and a /proc of its own, and Docker's defaults
# forbid both. LWJWAE_DOCKER_TIMEOUT caps
# the run (default 20 minutes), so a hung test ends instead of blocking.
#
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
image=lwjwae-test
timeout=${LWJWAE_DOCKER_TIMEOUT:-1200}
if [ $# -eq 0 ]; then
    set -- displayTest -Dlwjwae.requireDisplay=true -Dlwjwae.screenshots=true
fi

docker build -q --build-arg UID="$(id -u)" -f "$root/Dockerfile.test" -t "$image" "$root/scripts" > /dev/null

mkdir -p "$root/build/docker"
docker run --rm \
    --security-opt seccomp=unconfined \
    --security-opt systempaths=unconfined \
    -v "$root:/src:ro" \
    -v lwjwae-gradle:/home/tester/.gradle \
    -v "$root/build/docker:/out" \
    "$image" bash -c '
        rsync -a --exclude build --exclude .gradle --exclude .git /src/ /work/
        status=0
        timeout '"$timeout"' xvfb-run -a dbus-run-session -- scripts/linux/with-window-manager.sh ./gradlew --no-daemon --console=plain --continue "$@" || status=$?
        [ $status -eq 124 ] && echo "Timed out after '"$timeout"' s"
        for module in */build; do
            name=${module%/build}
            mkdir -p /out/$name
            cp -r $module/test-results $module/screenshots /out/$name/ 2>/dev/null || true
        done
        exit $status
    ' bash "$@"
