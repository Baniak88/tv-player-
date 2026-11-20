#!/usr/bin/env bash
set -euo pipefail
# These are the UI, player, resources and build configuration of the Fire Stick TV 0.9.22.
base=b179ac9828ddb9973466435837adc3fa1c0f8c51
git cat-file -e "$base^{commit}"
git diff --exit-code "$base" -- tv app/src/main
echo 'FIRESTICK_0_9_16_SOURCES_UNCHANGED'
