#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
version=6.1.6
checksum=d4fcb164028dd3beee5d92c0ac72e46aac6973c75ea12dc14de07bf8f407370a
cache="$root/.codec-cache"
archive="$cache/ffmpeg-$version.tar.xz"
source_dir="$cache/ffmpeg-$version"
destination="$root/decoder-ffmpeg/src/main/jniLibs"
ndk="${ANDROID_NDK_HOME:-${ANDROID_HOME:?Set ANDROID_HOME}/ndk/26.1.10909125}"
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64"
test -x "$toolchain/bin/clang" || { echo 'Install Android NDK 26.1.10909125 first.' >&2; exit 1; }
mkdir -p "$cache" "$destination"
if [ ! -f "$archive" ]; then curl --fail --location --retry 2 "https://ffmpeg.org/releases/ffmpeg-$version.tar.xz" -o "$archive"; fi
printf '%s  %s\n' "$checksum" "$archive" | sha256sum --check --status
if [ ! -d "$source_dir" ]; then tar -xJf "$archive" -C "$cache"; fi
decoders=aac,ac3,eac3,mp2,mp3,flac,alac,vorbis,opus,dca,truehd,mlp,amrnb,amrwb,pcm_mulaw,pcm_alaw
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    case "$abi" in
        armeabi-v7a) arch=arm; target=armv7a-linux-androideabi21; extra=(--cpu=armv7-a) ;;
        arm64-v8a) arch=aarch64; target=aarch64-linux-android21; extra=(--cpu=armv8-a) ;;
        x86) arch=x86; target=i686-linux-android21; extra=(--cpu=i686 --disable-asm) ;;
        x86_64) arch=x86_64; target=x86_64-linux-android21; extra=(--cpu=x86-64 --disable-asm) ;;
    esac
    output="$cache/build-$abi"
    mkdir -p "$output" "$destination/$abi"
    (
        cd "$output"
        "$source_dir/configure" --target-os=android --arch="$arch" "${extra[@]}" \
            --enable-cross-compile --cc="$toolchain/bin/$target-clang" --cxx="$toolchain/bin/$target-clang++" \
            --ar="$toolchain/bin/llvm-ar" --ranlib="$toolchain/bin/llvm-ranlib" --nm="$toolchain/bin/llvm-nm" --strip="$toolchain/bin/llvm-strip" \
            --disable-autodetect --disable-everything --disable-doc --disable-programs --disable-debug \
            --disable-network --disable-shared --enable-static --enable-pic --disable-gpl --disable-nonfree --disable-version3 \
            --disable-avdevice --disable-avformat --disable-avfilter --disable-swscale --disable-postproc \
            --enable-avcodec --enable-avutil --enable-swresample --enable-decoder="$decoders" \
            --extra-cflags='-fPIC -fstack-protector-strong'
        make -j"${CODEC_BUILD_JOBS:-2}"
        "$toolchain/bin/$target-clang++" -std=c++11 -shared -fPIC -O2 -fstack-protector-strong -static-libstdc++ \
            -I"$output" -I"$source_dir" "$root/decoder-ffmpeg/src/main/jni/ffmpeg_jni.cc" \
            -Wl,--start-group libavcodec/libavcodec.a libswresample/libswresample.a libavutil/libavutil.a -Wl,--end-group \
            -lm -lz -llog -landroid -pthread -Wl,--no-undefined -Wl,-Bsymbolic -Wl,-z,max-page-size=16384 -Wl,-z,relro,-z,now \
            -o "$destination/$abi/libffmpegJNI.so"
        "$toolchain/bin/llvm-strip" --strip-unneeded "$destination/$abi/libffmpegJNI.so"
        "$toolchain/bin/llvm-readelf" -d "$destination/$abi/libffmpegJNI.so"
        grep -q 'CONFIG_GPL 0' config.h
        grep -q 'CONFIG_NONFREE 0' config.h
        grep -q 'CONFIG_VERSION3 0' config.h
        cp config.h "$destination/$abi/"
    )
done
printf 'FFmpeg %s\nSHA-256 %s\nAndroid NDK 26.1.10909125; API 21; audio-only LGPL 2.1+\nDecoders: %s\n' "$version" "$checksum" "$decoders" > "$destination/BUILD.txt"
