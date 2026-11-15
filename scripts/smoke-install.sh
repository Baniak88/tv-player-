#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-output
trap 'adb logcat -d -v brief -s AndroidRuntime LibraryBenchmark RotationTest TestRunner ActivityManager > smoke-output/diagnostics.txt 2>&1; tail -n 180 smoke-output/diagnostics.txt' EXIT
adb install apk/debug/app-debug.apk
adb shell am start -W -n pl.tvplayer.preview/pl.tvplayer.HomeActivity
sleep 5
for attempt in {1..10}; do
  if ! adb shell uiautomator dump /sdcard/window.xml || ! adb pull /sdcard/window.xml smoke-output/library.xml; then sleep 2; continue; fi
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
adb shell run-as pl.tvplayer.preview rm -f files/rotation-ready files/rotation-landscape-ok
adb emu sensor set acceleration 0:9.81:0
adb shell am instrument -w -r -e notClass pl.tvplayer.SeriesActivityTest#televisionRemoteReachesSeasonPickerAndEpisodePlayback pl.tvplayer.preview.test/androidx.test.runner.AndroidJUnitRunner | tee smoke-output/navigation-test.txt &
test_pid=$!
landscape_sent=0
portrait_sent=0
while kill -0 "$test_pid" 2>/dev/null; do
  if [ "$landscape_sent" = 0 ] && adb shell run-as pl.tvplayer.preview test -f files/rotation-ready >/dev/null 2>&1; then
    adb emu sensor set acceleration 9.81:0:0
    landscape_sent=1
  fi
  if [ "$landscape_sent" = 1 ] && [ "$portrait_sent" = 0 ] && adb shell run-as pl.tvplayer.preview test -f files/rotation-landscape-ok >/dev/null 2>&1; then
    adb emu sensor set acceleration 0:9.81:0
    portrait_sent=1
  fi
  sleep 0.3
done
wait "$test_pid"
test "$landscape_sent" = 1
test "$portrait_sent" = 1
for name in home-portrait home-landscape sport-bouquet bouquets-grid epg-channel-list epg-program-dialog epg-stream-list epg-stream-dialog series-details series-season2 series-single-season movie-details tv-preview-portrait tv-preview-landscape; do
  adb exec-out run-as pl.tvplayer.preview cat "files/$name.png" > "smoke-output/$name.png" || true
done

grep -q 'OK (38 tests)' smoke-output/navigation-test.txt





