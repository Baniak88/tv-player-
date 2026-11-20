#!/usr/bin/env bash
set -euo pipefail
# These are the UI, player, resources and build configuration of the Fire Stick TV 0.9.19 (panel series for M3U sources, load report).
base=2727f14eaa0e74e16e2b2101b4f8a94ad657814a
git cat-file -e "$base^{commit}"
git diff --exit-code "$base" -- tv app/src/main
echo 'FIRESTICK_0_9_16_SOURCES_UNCHANGED'
