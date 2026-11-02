#!/usr/bin/env bash
set -euo pipefail
mkdir -p tv-smoke-output
cleanup() {
  adb logcat -d -v brief -s AndroidRuntime TestRunner SeriesRemote > tv-smoke-output/runtime.txt
  tail -n 200 tv-smoke-output/runtime.txt
  for name in tv-home tv-movies tv-bouquet series-television; do
    adb exec-out run-as pl.tvplayer.television cat "files/$name.png" > "tv-smoke-output/$name.png" || true
  done
}
trap cleanup EXIT
adb shell settings put system accelerometer_rotation 0
adb shell wm size 720x1280
adb shell wm density 160
adb install apk/debug/tv-debug.apk
adb shell am start -W -n pl.tvplayer.television/pl.tvplayer.TvActivity
sleep 3
adb install -r apk/debug/tv-debug.apk
adb install apk/androidTest/debug/tv-debug-androidTest.apk
adb shell am force-stop pl.tvplayer.television
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am instrument -w -r -e class "pl.tvplayer.SeriesActivityTest#televisionRemoteReachesSeasonPickerAndEpisodePlayback" pl.tvplayer.television.test/androidx.test.runner.AndroidJUnitRunner | tee tv-smoke-output/series-remote.txt
grep -q 'OK (1 test)' tv-smoke-output/series-remote.txt
for case in homeAndMoviesWorkWithRemoteOnly bouquetsAndItemOptionsWorkWithRemote credentialsAreEncryptedAndReadableAfterRestart playerKeepsLandscapeAndHandlesRemoteMenu; do
  adb shell am force-stop pl.tvplayer.television
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard
  adb shell am instrument -w -r -e class "pl.tvplayer.TvNavigationTest#$case" pl.tvplayer.television.test/androidx.test.runner.AndroidJUnitRunner | tee "tv-smoke-output/$case.txt"
  grep -q 'OK (1 test)' "tv-smoke-output/$case.txt"
done
cat tv-smoke-output/*Remote*.txt tv-smoke-output/credentials*.txt > tv-smoke-output/navigation.txt
echo 'TV_REMOTE_CHECKS_PASSED=5' | tee -a tv-smoke-output/navigation.txt
for name in tv-home tv-movies tv-bouquet series-television; do
  adb exec-out run-as pl.tvplayer.television cat "files/$name.png" > "tv-smoke-output/$name.png"
done
grep -q 'TV_REMOTE_CHECKS_PASSED=5' tv-smoke-output/navigation.txt

