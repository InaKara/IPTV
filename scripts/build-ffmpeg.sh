#!/bin/bash
# AU-1: pinned audio-only Android build; Linux/WSL, no global installs.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.private/ffmpeg-build"
NDK="$WORK/ndk/android-ndk-r26b"
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
export PATH="$NDK/prebuilt/linux-x86_64/bin:$BIN:$PATH"
SOURCE="$WORK/ffmpeg/ffmpeg-6.0.1"
JNI="$ROOT/third_party/media3-ffmpeg/jni/ffmpeg_jni.cc"
if [ "$#" -eq 0 ]; then set -- armeabi-v7a arm64-v8a; fi
for ABI in "$@"; do
 case "$ABI" in
  armeabi-v7a) TRIPLE=armv7a-linux-androideabi; ARCH=arm; CPU=armv7-a; EXTRA=(--extra-cflags='-march=armv7-a -mfloat-abi=softfp -ffunction-sections -fdata-sections -fvisibility=hidden');;
  arm64-v8a) TRIPLE=aarch64-linux-android; ARCH=aarch64; CPU=armv8-a; EXTRA=(--extra-cflags='-ffunction-sections -fdata-sections -fvisibility=hidden');;
  x86_64) TRIPLE=x86_64-linux-android; ARCH=x86_64; CPU=x86-64; EXTRA=(--disable-x86asm --extra-cflags='-ffunction-sections -fdata-sections -fvisibility=hidden');;
  *) echo "Unsupported ABI: $ABI"; exit 1;;
 esac
 OUT="$WORK/objects/$ABI"
 mkdir -p "$OUT"
 cd "$OUT"
 CONFIGURE=(--target-os=android --arch="$ARCH" --cpu="$CPU" --enable-cross-compile \
  --cc="$BIN/${TRIPLE}26-clang" --cxx="$BIN/${TRIPLE}26-clang++" \
  --ar="$BIN/llvm-ar" --nm="$BIN/llvm-nm" --ranlib="$BIN/llvm-ranlib" --strip="$BIN/llvm-strip" \
  --enable-static --disable-shared --enable-pic --disable-doc --disable-programs --disable-everything \
  --disable-avdevice --disable-avformat --disable-swscale --disable-postproc --disable-avfilter \
  --disable-symver --disable-v4l2-m2m --disable-vulkan --disable-autodetect --disable-network \
  --enable-swresample --enable-decoder=mp3 --optflags=-Oz "${EXTRA[@]}")
 # Reuse actual configure output only when its recorded arguments match exactly.
 if ! python3 - "${CONFIGURE[@]}" <<'PY'
import pathlib, shlex, sys
config = pathlib.Path('ffbuild/config.mak')
if not config.exists(): sys.exit(1)
line = next((x for x in config.read_text().splitlines() if x.startswith('FFMPEG_CONFIGURATION=')), '')
sys.exit(0 if shlex.split(line.partition('=')[2]) == sys.argv[1:] else 1)
PY
 then "$SOURCE/configure" "${CONFIGURE[@]}" > configure.log 2>&1; fi
 make -j4 > make.log 2>&1
 # FFmpeg's root 'version' file must not shadow libc++'s <version> header.
 "$BIN/${TRIPLE}26-clang++" -std=c++11 -Oz -fPIC -fvisibility=hidden -I"$OUT" -idirafter "$SOURCE" -c "$JNI" -o ffmpeg_jni.o > jni.log 2>&1
 "$BIN/${TRIPLE}26-clang++" -shared -static-libstdc++ -Wl,-Bsymbolic -Wl,--gc-sections -Wl,--exclude-libs,ALL \
  -Wl,-soname,libffmpegJNI.so -o libffmpegJNI.so ffmpeg_jni.o \
  -Wl,--start-group libswresample/libswresample.a libavcodec/libavcodec.a libavutil/libavutil.a -Wl,--end-group \
  -llog -landroid -lm >> jni.log 2>&1
 # x86_64 is a local Bionic decoder-check target, not an APK ABI in this scope.
 if [ "$ABI" = x86_64 ]; then DEST="$OUT/check-library";
 else DEST="$ROOT/app/build/generated/ffmpeg/jniLibs/$ABI"; fi
 mkdir -p "$DEST"
 cp libffmpegJNI.so "$DEST/libffmpegJNI.so"
 "$BIN/llvm-strip" --strip-unneeded "$DEST/libffmpegJNI.so"
 printf 'BUILT %s ' "$ABI"
 wc -c < "$DEST/libffmpegJNI.so"
done
