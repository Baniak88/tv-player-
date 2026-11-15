#!/usr/bin/env bash
set -euo pipefail
mkdir -p tv-smoke-output
failures=0
cleanup() {
  adb logcat -d -v brief -s AndroidRuntime TestRunner SeriesRemote TvTestUi StreamingState > tv-smoke-output/runtime.txt
  tail -n 200 tv-smoke-output/runtime.txt
  for name in tv-home tv-movies tv-bouquet series-television movie-television tv-phone-setup tv096-streaming-home tv096-shelves tv096-bouquet-return tv096-streaming-series tv096-streaming-seasons tv096-failure tv096-live-info tv096-live-960 tv096-live-1280 tv096-movies-960 tv096-movies-1280 tv096-movies-list-960 tv096-movies-list-1280 tv096-series-960 tv096-episodes-960; do
    adb exec-out run-as pl.tvplayer.television cat "files/$name.png" > "tv-smoke-output/$name.png" || true
  done
}
trap cleanup EXIT
run_case() {
  local test_class="$1" report="$2" expected="${3:-1}"
  adb shell am force-stop pl.tvplayer.television
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard
  adb shell am instrument -w -r -e class "$test_class" pl.tvplayer.television.test/androidx.test.runner.AndroidJUnitRunner | tee "tv-smoke-output/$report.txt"
  if ! grep -q "OK ($expected test" "tv-smoke-output/$report.txt"; then failures=$((failures+1)); fi
}
adb shell settings put system accelerometer_rotation 0
physical_size=$(adb shell wm size | tr -d '\r')
if [[ "$physical_size" =~ Physical\ size:\ ([0-9]+)x([0-9]+) ]] && (( BASH_REMATCH[1] > BASH_REMATCH[2] )); then
  adb shell wm size 1280x720
else
  adb shell wm size 720x1280
fi
adb shell wm density 160
adb install apk/debug/tv-debug.apk
adb shell am start -W -n pl.tvplayer.television/pl.tvplayer.TvActivity
sleep 3
adb install -r apk/debug/tv-debug.apk
adb install apk/androidTest/debug/tv-debug-androidTest.apk
run_case 'pl.tvplayer.SeriesActivityTest#televisionRemoteReachesSeasonPickerAndEpisodePlayback' series-remote
for case in homeAndMoviesWorkWithRemoteOnly oktvHomeReachesManagementAndAboutWithRemote bouquetsAndItemOptionsWorkWithRemote credentialsAreEncryptedAndReadableAfterRestart playerKeepsLandscapeAndHandlesRemoteMenu; do
  run_case "pl.tvplayer.TvNavigationTest#$case" "$case"
done
for case in catalogOpensMovieDetailsAndRemoteCanFavoriteAndPlay m3uMovieCanResumeAndRestartWithoutMetadataApi; do
  run_case "pl.tvplayer.TvMovieDetailsTest#$case" "$case"
done
cat tv-smoke-output/series-remote.txt tv-smoke-output/homeAndMoviesWorkWithRemoteOnly.txt tv-smoke-output/oktvHomeReachesManagementAndAboutWithRemote.txt tv-smoke-output/bouquetsAndItemOptionsWorkWithRemote.txt tv-smoke-output/credentialsAreEncryptedAndReadableAfterRestart.txt tv-smoke-output/playerKeepsLandscapeAndHandlesRemoteMenu.txt tv-smoke-output/catalogOpensMovieDetailsAndRemoteCanFavoriteAndPlay.txt tv-smoke-output/m3uMovieCanResumeAndRestartWithoutMetadataApi.txt > tv-smoke-output/navigation.txt
if ((failures==0)); then echo 'TV_REMOTE_CHECKS_PASSED=8' | tee -a tv-smoke-output/navigation.txt; fi
run_case 'pl.tvplayer.PlaybackRecoveryTest,pl.tvplayer.PlaybackBandwidthTest,pl.tvplayer.StreamResumptionTest' playback 8
run_case 'pl.tvplayer.TvPhoneSetupTest' phone-setup
run_case 'pl.tvplayer.TvSettingsTest' settings 4
run_case 'pl.tvplayer.TvRecordingTest' recording 3
run_case 'pl.tvplayer.TvPlaybackOptionsTest' playback-options 2
run_case 'pl.tvplayer.TvSourceImportTest' source-import 2
run_case 'pl.tvplayer.TvCatalogUsabilityTest' catalog-usability 3
run_case 'pl.tvplayer.TvLiveInfoTest' live-info-audio 2
run_case 'pl.tvplayer.TvStreamingNavigationTest' streaming-navigation 6
run_case 'pl.tvplayer.TvAudioCodecsTest' software-audio-codecs 7
adb exec-out run-as pl.tvplayer.television cat files/tv096-live-info.png > tv-smoke-output/tv096-live-info.png || true
for name in tv096-live-960 tv096-live-1280 tv096-movies-960 tv096-movies-1280 tv096-movies-list-960 tv096-movies-list-1280 tv096-series-960 tv096-episodes-960; do
  adb exec-out run-as pl.tvplayer.television cat "files/$name.png" > "tv-smoke-output/$name.png" || true
done
for name in tv-home tv-movies tv-bouquet series-television movie-television tv-phone-setup tv096-streaming-home tv096-shelves tv096-bouquet-return tv096-streaming-series tv096-streaming-seasons tv096-failure tv096-live-info tv096-live-960 tv096-live-1280 tv096-movies-960 tv096-movies-1280 tv096-movies-list-960 tv096-movies-list-1280 tv096-series-960 tv096-episodes-960; do
  adb exec-out run-as pl.tvplayer.television cat "files/$name.png" > "tv-smoke-output/$name.png" || true
done
if ((failures>0)); then echo "TV_NATIVE_FAILED_SUITES=$failures"; exit 1; fi
echo "TV_NATIVE_CHECKS_PASSED=46"
