#!/usr/bin/env bash
set -euo pipefail
mkdir -p tv-smoke-output
cleanup() {
  adb logcat -d -v brief -s AndroidRuntime TestRunner SeriesRemote > tv-smoke-output/runtime.txt
  tail -n 200 tv-smoke-output/runtime.txt
  for name in tv-home tv-movies tv-bouquet series-television movie-television tv-phone-setup; do
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
  grep -q "OK ($expected test" "tv-smoke-output/$report.txt"
}
adb shell settings put system accelerometer_rotation 0
adb shell wm size 720x1280
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
echo 'TV_REMOTE_CHECKS_PASSED=8' | tee -a tv-smoke-output/navigation.txt
run_case 'pl.tvplayer.PlaybackRecoveryTest,pl.tvplayer.PlaybackBandwidthTest,pl.tvplayer.StreamResumptionTest' playback 8
run_case 'pl.tvplayer.TvPhoneSetupTest' phone-setup
run_case 'pl.tvplayer.TvSettingsTest' settings 4
run_case 'pl.tvplayer.TvRecordingTest' recording 3
run_case 'pl.tvplayer.TvPlaybackOptionsTest' playback-options 2
run_case 'pl.tvplayer.TvSourceImportTest' source-import 2
run_case 'pl.tvplayer.TvCatalogUsabilityTest' catalog-usability 3
run_case 'pl.tvplayer.TvLiveInfoTest' live-info-audio 2
adb exec-out run-as pl.tvplayer.television cat files/tv095-live-info.png > tv-smoke-output/tv095-live-info.png
for name in tv095-live-960 tv095-live-1280 tv095-movies-960 tv095-movies-1280 tv095-movies-list-960 tv095-movies-list-1280 tv095-series-960 tv095-episodes-960; do
  adb exec-out run-as pl.tvplayer.television cat "files/$name.png" > "tv-smoke-output/$name.png"
done
for name in tv-home tv-movies tv-bouquet series-television movie-television tv-phone-setup; do
  adb exec-out run-as pl.tvplayer.television cat "files/$name.png" > "tv-smoke-output/$name.png"
done
grep -q 'TV_REMOTE_CHECKS_PASSED=8' tv-smoke-output/navigation.txt
