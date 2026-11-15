#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
output="$root/tv/src/androidTest/assets/generated-audio"
mkdir -p "$output"
for codec in aac ac3 eac3 mp2 dca; do
    case "$codec" in
        ac3|eac3) audio='aevalsrc=0|0|0.35*sin(2*PI*440*t)|0|0|0:s=48000:c=5.1'; bitrate=256k; suffix=ts; extra=() ;;
        dca) audio='aevalsrc=0.25*sin(2*PI*440*t)|0.25*sin(2*PI*660*t):s=48000:c=stereo'; bitrate=768k; suffix=mkv; extra=(-strict -2) ;;
        *) audio='aevalsrc=0.25*sin(2*PI*440*t)|0.25*sin(2*PI*660*t):s=48000:c=stereo'; bitrate=128k; suffix=ts; extra=() ;;
    esac
    ffmpeg -hide_banner -loglevel error -y -f lavfi -i color=c=navy:s=160x90:r=5 -f lavfi -i "$audio" \
        -t 30 -c:v libx264 -threads 1 -preset ultrafast -pix_fmt yuv420p -c:a "$codec" -b:a "$bitrate" "${extra[@]}" "$output/$codec.$suffix"
done
