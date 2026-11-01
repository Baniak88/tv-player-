#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-output
trap 'adb logcat -d -v brief -s AndroidRuntime LibraryBenchmark TestRunner ActivityManager > smoke-output/diagnostics.txt 2>&1; tail -n 180 smoke-output/diagnostics.txt' EXIT
adb install apk/debug/app-debug.apk
adb shell am start -W -n pl.tvplayer.preview/pl.tvplayer.HomeActivity
sleep 5
for attempt in {1..10}; do
  adb shell uiautomator dump /sdcard/window.xml
  adb pull /sdcard/window.xml smoke-output/library.xml
  if grep -q 'Telewizja na żywo' smoke-output/library.xml; then break; fi
  sleep 2
done
if ! grep -q 'Telewizja na żywo' smoke-output/library.xml; then cat smoke-output/library.xml; exit 1; fi
adb shell am force-stop pl.tvplayer.preview
adb shell am start -W -n pl.tvplayer.preview/pl.tvplayer.HomeActivity
sleep 2
adb exec-out screencap -p > smoke-output/library.png
adb install -r apk/debug/app-debug.apk
adb logcat -d -s AndroidRuntime > smoke-output/runtime.txt
if grep -q 'FATAL EXCEPTION' smoke-output/runtime.txt; then
  cat smoke-output/runtime.txt
  exit 1
fi

adb install apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r pl.tvplayer.preview.test/androidx.test.runner.AndroidJUnitRunner | tee smoke-output/navigation-test.txt
grep -q 'OK (5 tests)' smoke-output/navigation-test.txt

for name in home-portrait home-landscape sport-bouquet; do
  adb exec-out run-as pl.tvplayer.preview cat "files/$name.png" > "smoke-output/$name.png"
done
