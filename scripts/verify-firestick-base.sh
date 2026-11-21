#!/usr/bin/env bash
set -euo pipefail
# These are the UI, player, resources and build configuration of the Fire Stick TV 0.9.23.
base=4152b2672064a1acc5e36df4ec3a9e8f84739cf4
git cat-file -e "$base^{commit}"
git diff --exit-code "$base" -- tv app/src/main
echo 'FIRESTICK_0_9_16_SOURCES_UNCHANGED'
