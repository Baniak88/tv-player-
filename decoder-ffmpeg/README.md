# Audio decoder for TV

The Java/JNI wrapper is the official AndroidX Media3 1.5.1 FFmpeg extension
(tag object `795ee2df9bdbce7461dd07b9d9a75fabe6d79208`). Its Apache 2.0 headers are retained.
Only audio classes are included. The only wrapper edit removes a Javadoc link to the omitted video renderer.
Source: https://github.com/androidx/media/tree/1.5.1/libraries/decoder_ffmpeg

`bash scripts/build-audio-codecs.sh` builds FFmpeg 6.1.6 from the upstream source
archive with the SHA-256 pinned in the script. The native build disables GPL,
nonfree, version-3-only components, networking, demuxers, video decoders and programs.
FFmpeg remains LGPL 2.1 or later. Native code targets Android API 21 and includes
armeabi-v7a, arm64-v8a, x86 and x86_64 with 16 KiB ELF alignment.
The AndroidX JNI wrapper is linked with the static FFmpeg audio libraries into
`libffmpegJNI.so`. Sources and build flags are sufficient to rebuild and relink it;
the preview signing key in this repository permits installing a rebuilt APK.

Install NDK `26.1.10909125`, set `ANDROID_HOME` (or `ANDROID_NDK_HOME`), run the script,
then build `:tv:assembleDebug`. The build refuses to package a TV APK missing any
of the four native libraries. Binaries and the downloaded FFmpeg archive are generated
files, not committed dependencies. CI caches the generated libraries using hashes
of the JNI source and build script, and uploads the corresponding FFmpeg source archive.

Original source archive: https://ffmpeg.org/releases/ffmpeg-6.1.6.tar.xz
SHA-256: `d4fcb164028dd3beee5d92c0ac72e46aac6973c75ea12dc14de07bf8f407370a`
Full license texts and notices are also packaged in the APK under `audio-licenses/`.

For native audio regressions run `bash scripts/make-audio-fixtures.sh` before
building the test APK. It generates original tone/solid-colour samples with AAC,
AC3, E-AC3, MPEG Layer II and DTS. AC3/E-AC3 contain centre-channel audio so stereo
downmix tests cannot pass by discarding dialogue channels.
