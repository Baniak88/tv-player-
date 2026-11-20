#!/usr/bin/env bash
set -euo pipefail
# These are the UI, player, resources and build configuration of the original TV 0.9.16.
base=67fa2975945dc4a92400c568ac855596e93e4a98
git cat-file -e "$base^{commit}"
git diff --exit-code "$base" -- tv app/src/main
echo 'FIRESTICK_0_9_16_SOURCES_UNCHANGED'
