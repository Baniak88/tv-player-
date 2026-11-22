#!/usr/bin/env bash
set -euo pipefail
# These are the UI, player, resources and build configuration of the Fire Stick TV 0.9.25.
base=8c46ac187b3423b30983f5e403da96679cfd6d9e
git cat-file -e "$base^{commit}"
git diff --exit-code "$base" -- tv app/src/main
echo 'FIRESTICK_0_9_16_SOURCES_UNCHANGED'
