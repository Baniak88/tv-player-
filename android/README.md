# Android 0.9.17 — responsive port

This module keeps the dark Fire Stick 0.9.16 visual style, with Android-specific portrait/landscape layouts and touch input. It builds `pl.tvplayer.preview` with versionCode 34 and the existing preview signing key. The original `:tv` module and `app/src/main` stay byte-for-byte unchanged in Git relative to TV commit `67fa2975945dc4a92400c568ac855596e93e4a98`.

Android variants live in `src/main/java/pl/tvplayer`: `TvActivity`, `TvShelfView`, `MovieActivity`, `SeriesActivity`, `PlayerActivity`, `TvLiveInfoBar` and `PlaybackNetwork`. They are based on the original TV/shared classes and contain phone layout, rotation and transport changes. `syncAndroidSharedSources` copies all remaining shared Java classes into a generated build directory, excluding those variants to avoid duplicate classes. The Android manifest belongs to this module and permits normal user-controlled orientation.

`AndroidApplication` supplies single-tap behavior, permanently visible previous/next channel arrows and optional toolbar actions. `AndroidLayout` handles narrow layouts and window insets. `AndroidHttpSource` follows HTTP/HTTPS redirects, makes at most one platform-agent compatibility fallback for HTTP 403/406, and can satisfy a rejected small range by skipping at most 2 MiB with a 15-second skip budget. Large rejected ranges remain errors rather than downloading an entire movie repeatedly. Authentication errors are not changed into compatibility retries. `PlayerActivity` shows the actual HTTP code and offers an explicit restart for an invalid resume position.

Verification: shared JVM tests, Android Lint, original TV source fingerprint, signature/package/launcher checks, eight playback recovery tests, nine touch/rotation cases and five real HTTP/TLS cases on API 35. The TLS fixture installs a temporary certificate factory only in the test process and restores it afterwards; the shipped app retains platform certificate validation.

Build with `gradle :android:assembleDebug :android:assembleDebugAndroidTest`. The preview APK is `android/build/outputs/apk/debug/android-debug.apk`. Changes in this module are not included in the Fire Stick APK.
