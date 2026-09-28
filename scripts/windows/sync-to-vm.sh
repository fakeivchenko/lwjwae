#!/usr/bin/env bash
#
# Copies the working tree to the Windows VM over SSH (no git involved) and optionally runs a
# Gradle task there. WIN_HOST names the ssh host (default: win), WIN_SRC_DIR the parent directory
# on the VM (default: C:\src):
#
#   scripts/windows/sync-to-vm.sh                       # copy only
#   scripts/windows/sync-to-vm.sh :lwjwae-windows:test
#
set -euo pipefail
host=${WIN_HOST:-win}
root=$(cd "$(dirname "$0")/../.." && pwd)
parent=${WIN_SRC_DIR:-'C:\src'}
remote="$parent\\$(basename "$root")"

# tar only adds; drop every source tree first so deleted files disappear on the VM too
ssh "$host" "New-Item -ItemType Directory -Force '$remote' | Out-Null; Get-ChildItem '$remote' -Directory -Filter src -Recurse -Depth 1 | Remove-Item -Recurse -Force" 2>/dev/null
tar --exclude=build --exclude=.gradle --exclude=.idea --exclude=.kotlin --exclude=.git -czf - -C "$(dirname "$root")" "$(basename "$root")" \
    | ssh "$host" "tar -xzf - -C '$parent'" 2>/dev/null

# tar.exe of Windows skips a name that isn't ASCII; scp keeps it in UTF-8
target="$(printf '%s' "$remote" | tr '\\' '/')"
(cd "$root" && find . \( -name build -o -name .gradle -o -name .idea -o -name .kotlin -o -name .git \) -prune \
    -o -type f -print) | LC_ALL=C grep '[^ -~]' | while IFS= read -r file; do
    scp -q "$root/${file#./}" "$host:$target/${file#./}" 2>/dev/null
done

if [ $# -gt 0 ]; then
    ssh "$host" "cd '$remote'; cmd /c \"gradlew.bat $* --console=plain 2>&1\"" 2>/dev/null
fi
