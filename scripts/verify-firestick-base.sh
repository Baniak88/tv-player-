#!/usr/bin/env bash
set -euo pipefail
# These are the UI, player, resources and build configuration of the Fire Stick TV 0.9.20.
base=f3a15c4d85c2fd4d17455c282ce950c9a2947314
git cat-file -e "$base^{commit}"
git diff --exit-code "$base" -- tv app/src/main
echo 'FIRESTICK_0_9_16_SOURCES_UNCHANGED'
