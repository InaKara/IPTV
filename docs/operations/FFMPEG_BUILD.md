# AU-1 / PLAY-010 native audio fallback

The app includes unmodified official Media3 1.2.1 FFmpeg Java/JNI sources under `third_party/media3-ffmpeg`. `EXTENSION_RENDERER_MODE_ON` places the extension after Android's audio renderer; it does not force software decoding for AAC or other supported platform audio. Only FFmpeg's `mp3` decoder is enabled. Despite its name, that decoder handles MPEG Layer II (MP2), matching Media3's `audio/mpeg-L2` mapping. Video remains on the existing Media3/Android route.

The Java build uses the official extension's compile-only Checker Framework 3.13.0 annotations. `app/lint.xml` applies the same explicit `UnstableApi` opt-in as upstream Media3 1.2.1 `libraries/lint.xml`, allowing its package-private decoder to use Media3 internals without editing upstream sources. The lint checks, including Compose checks, stay enabled; no baseline hides errors.

## Rebuild locally

Use Linux or Ubuntu WSL with Python 3.12+, bash and standard Unix utilities. The pinned Linux NDK supplies clang and make; no system compiler, global SDK update, CMake, Maven FFmpeg binary or device access is required. Run from repository root:

```sh
python3 scripts/prepare-ffmpeg.py
bash scripts/build-ffmpeg.sh
python3 scripts/verify-ffmpeg.py --record
```

On Windows, invoke those commands through the installed Ubuntu WSL distribution using the workspace's `/mnt/...` path. Preparation verifies SHA-256 for the official NDK r26b, Media3 1.2.1 archive and FFmpeg 6.0.1 archive against `scripts/ffmpeg-sources.json`, safely extracts into `.private/ffmpeg-build`, and compares vendored sources with upstream. A first preparation downloads about 810 MB; extraction on a Windows-mounted filesystem can take several minutes. No downloads occur during ordinary Gradle builds.

The build targets Android API 26, `armeabi-v7a` (the current Firestick) and `arm64-v8a`. It produces stripped `libffmpegJNI.so` in `app/build/generated/ffmpeg/jniLibs`. Intermediate objects, static archives, full configure/make/JNI logs and the unstripped linked library remain in `.private/ffmpeg-build/objects/<ABI>`. The verification command checks ELF machine/class, all declared JNI exports, MPEG-only/LGPL configuration, system-only dependencies and records input/output hashes. Gradle refuses missing/stale artifacts instead of silently building without the fallback. After `clean`, repeat the build and record commands. After native-source/tool/script edits, repeat preparation, build and record; do not just regenerate hashes for an old library. Repeated builds reuse configure output only when its recorded arguments match exactly, and make remains incremental. JNI always relinks. This APK targets ARM; Intel Android compatibility is not included.

Use the existing JDK 17/debug workflow afterwards:

```powershell
.\test-smoke.ps1 -Lint
.\gradlew.bat :app:assembleDebug
```

Then verify the APK using Python (does not require Linux unless `--record` is used):

```sh
python3 scripts/verify-ffmpeg.py --apk app/build/outputs/apk/debug/app-debug.apk
```

This verifies both ARM libraries match the prepared binaries and license assets are packaged. Local build/native checks do not prove Android dynamic loading, actual track selection, audible output or video/audio synchronization on Firestick. Deployment/app starts retain their separate approval policy.

## Local native decoding check

```sh
bash scripts/check-mp2.sh
```

This builds the same configured FFmpeg sources for Android x86_64 and runs a static Android Bionic decoder/resampler executable under the WSL Linux kernel. That executable decodes the fixed synthetic MP2 fixture using the same `mp3` decoder, verifies 48 kHz stereo, all 84 frames/193,536 PCM samples, non-silent RMS/peak bounds and absence of the AAC decoder. This tests native decoding, not a Java VM/JNI load, ARM execution or audible output. The x86_64 library stays in private check output and is not packaged. The slower native work is explicitly excluded from the default 90-second smoke target.

The 48,384-byte fixture at `scripts/fixtures/tone-48khz-stereo.mp2` was generated locally from a 997 Hz sine tone, without provider media:

```sh
ffmpeg -f lavfi -i sine=frequency=997:sample_rate=48000:duration=2 -ac 2 -c:a mp2 -b:a 192k tone-48khz-stereo.mp2
```

SHA-256: `cf64a921217248c0a2bce514f7b64fa7d8f335f53254f475450ce2935ec66add`. Desktop FFmpeg is needed only to regenerate this fixture, not for the native check itself.

## Notices, source and relinking

Media3 sources retain Apache 2.0 notices and license in `third_party/media3-ffmpeg/LICENSE.txt`. FFmpeg is configured without GPL/nonfree components; its LGPL 2.1-or-later license is bundled in `app/src/main/assets/licenses/`. These texts and `NOTICES.txt` are included in the APK. FFmpeg source is unmodified; build flags are in `scripts/build-ffmpeg.sh`.

Keep the exact source archives, lock file, these scripts and vendored sources with any distributed binary. FFmpeg is statically linked into the JNI shared library: the retained `ffmpeg_jni.o`, static archives and command in the build script allow relinking a modified FFmpeg library and replacing the JNI `.so`, then rebuilding/reinstalling the app. Preserve/provide corresponding source and relinking material when distributing the app; an APK by itself is not the complete source/relinking bundle. Ordinary personal debug deployment remains the current scope; no publication is authorized.
