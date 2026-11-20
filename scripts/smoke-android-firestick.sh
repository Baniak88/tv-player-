#!/usr/bin/env bash
set -euo pipefail
mkdir -p android-tv-smoke-output
cleanup() {
  adb logcat -d -v brief -s AndroidRuntime TestRunner > android-tv-smoke-output/runtime.txt
  for name in android-touch-failure android-firestick-home android-firestick-movies android-firestick-series android-firestick-film android-firestick-serial android-firestick-tv android-home-portrait android-movies-portrait android-series-portrait android-series-landscape android-tv-portrait android-tv-landscape android-player-tv-portrait android-player-tv-landscape android-film-portrait android-film-landscape android-serial-portrait android-serial-landscape android-player-film-portrait; do
    adb exec-out run-as pl.tvplayer.preview cat "files/$name.png" > "android-tv-smoke-output/$name.png" || true
  done
}
trap cleanup EXIT
adb shell settings put secure immersive_mode_confirmations confirmed
adb install apk/debug/android-debug.apk
adb shell am start -W -n pl.tvplayer.preview/pl.tvplayer.TvActivity
adb install -r apk/debug/android-debug.apk
adb install apk/androidTest/debug/android-debug-androidTest.apk
adb shell am force-stop pl.tvplayer.preview
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am instrument -w -r -e class pl.tvplayer.PlaybackRecoveryTest,pl.tvplayer.PlaybackBandwidthTest,pl.tvplayer.StreamResumptionTest pl.tvplayer.preview.test/androidx.test.runner.AndroidJUnitRunner | tee android-tv-smoke-output/playback.txt
grep -q 'OK (8 tests)' android-tv-smoke-output/playback.txt
adb shell am force-stop pl.tvplayer.preview
# Configure the phone before its process starts, so the first touch test is not
# observing an Activity being recreated during a density change.
adb shell wm size 400x800
adb shell wm density 160
adb shell wm user-rotation lock 1
adb shell am instrument -w -r -e class pl.tvplayer.AndroidResponsiveTest pl.tvplayer.preview.test/androidx.test.runner.AndroidJUnitRunner | tee android-tv-smoke-output/touch.txt
grep -q 'OK (9 tests)' android-tv-smoke-output/touch.txt
adb shell am force-stop pl.tvplayer.preview
adb shell am instrument -w -r -e class pl.tvplayer.AndroidMovieHttpTest pl.tvplayer.preview.test/androidx.test.runner.AndroidJUnitRunner | tee android-tv-smoke-output/http.txt
grep -q 'OK (5 tests)' android-tv-smoke-output/http.txt
echo 'ANDROID_RESPONSIVE_CHECKS_PASSED=22'
