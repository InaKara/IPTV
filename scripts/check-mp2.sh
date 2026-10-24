#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.private/ffmpeg-build"
BIN="$WORK/ndk/android-ndk-r26b/toolchains/llvm/prebuilt/linux-x86_64/bin"
OUT="$WORK/objects/x86_64"
SOURCE="$WORK/ffmpeg/ffmpeg-6.0.1"
bash "$ROOT/scripts/build-ffmpeg.sh" x86_64
"$BIN/x86_64-linux-android26-clang" -static -Oz -I"$OUT" -I"$SOURCE" \
 "$ROOT/scripts/check-mp2.c" -o "$OUT/check-mp2" \
 -Wl,--start-group "$OUT/libswresample/libswresample.a" "$OUT/libavcodec/libavcodec.a" "$OUT/libavutil/libavutil.a" -Wl,--end-group -lm
"$OUT/check-mp2" "$ROOT/scripts/fixtures/tone-48khz-stereo.mp2"
