#!/usr/bin/env bash
set -euo pipefail
mkdir -p tv-smoke-output
trap 'adb logcat -d -v brief -s AndroidRuntime TestRunner > tv-smoke-output/runtime.txt' EXIT
adb shell settings put system accelerometer_rotation 0
adb shell wm size 1280x720
adb shell wm density 160
adb install apk/debug/tv-debug.apk
adb shell am start -W -n pl.tvplayer.television/pl.tvplayer.TvActivity
sleep 3
adb install -r apk/debug/tv-debug.apk
adb install apk/androidTest/debug/tv-debug-androidTest.apk
adb shell am instrument -w -r pl.tvplayer.television.test/androidx.test.runner.AndroidJUnitRunner | tee tv-smoke-output/navigation.txt
for name in tv-home tv-movies tv-bouquet; do
  adb exec-out run-as pl.tvplayer.television cat "files/$name.png" > "tv-smoke-output/$name.png"
done
grep -q 'OK (4 tests)' tv-smoke-output/navigation.txt
