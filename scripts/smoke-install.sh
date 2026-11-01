#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-output
adb install apk/debug/app-debug.apk
adb shell am start -W -n pl.tvplayer.preview/pl.tvplayer.MainActivity
sleep 5
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml smoke-output/library.xml
grep -q 'Twoja biblioteka' smoke-output/library.xml
adb shell am force-stop pl.tvplayer.preview
adb shell am start -W -n pl.tvplayer.preview/pl.tvplayer.MainActivity
sleep 2
adb exec-out screencap -p > smoke-output/library.png
adb install -r apk/debug/app-debug.apk
adb logcat -d -s AndroidRuntime > smoke-output/runtime.txt
if grep -q 'FATAL EXCEPTION' smoke-output/runtime.txt; then
  cat smoke-output/runtime.txt
  exit 1
fi

adb install apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w pl.tvplayer.preview.test/android.test.InstrumentationTestRunner | tee smoke-output/navigation-test.txt
grep -q 'OK (1 test)' smoke-output/navigation-test.txt
