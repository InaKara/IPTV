"""Record/verify AU-1 provenance, ELF dependencies, JNI symbols and APK packaging."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
GENERATED = ROOT / "app/build/generated/ffmpeg"
ABIS = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183)}
BIN = ROOT / ".private/ffmpeg-build/ndk/android-ndk-r26b/toolchains/llvm/prebuilt/linux-x86_64/bin"

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def provenance():
    files = [ROOT / f"scripts/{name}" for name in
             ("ffmpeg-sources.json", "build-ffmpeg.sh", "prepare-ffmpeg.py", "verify-ffmpeg.py")]
    files += [p for p in (ROOT / "third_party/media3-ffmpeg").rglob("*") if p.is_file()]
    return {str(p.relative_to(ROOT).as_posix()): digest(p) for p in sorted(files)}

def check_elf(data, abi):
    elf_class, machine = ABIS[abi]
    assert data[:4] == b"\x7fELF" and data[4] == elf_class and data[5] == 1, abi
    assert int.from_bytes(data[18:20], "little") == machine, abi

parser = argparse.ArgumentParser()
parser.add_argument("--record", action="store_true")
parser.add_argument("--apk", type=pathlib.Path)
args = parser.parse_args()
libraries = {}
for abi in ABIS:
    path = GENERATED / f"jniLibs/{abi}/libffmpegJNI.so"
    check_elf(path.read_bytes(), abi)
    libraries[abi] = digest(path)
    if args.record:
        objects = ROOT / f".private/ffmpeg-build/objects/{abi}"
        components = (objects / "config_components.h").read_text()
        assert re.findall(r"#define (CONFIG_\w+_DECODER) 1", components) == ["CONFIG_MP3_DECODER"]
        configuration = (objects / "config.h").read_text()
        assert '#define FFMPEG_LICENSE "LGPL version 2.1 or later"' in configuration
        assert "#define CONFIG_GPL 0" in configuration and "#define CONFIG_NONFREE 0" in configuration
        dynamic = subprocess.check_output([str(BIN / "llvm-readelf"), "--dynamic", str(path)], text=True)
        needed = [line.split("[")[1].split("]")[0] for line in dynamic.splitlines() if "(NEEDED)" in line]
        assert set(needed) <= {"liblog.so", "libandroid.so", "libm.so", "libdl.so", "libc.so"}, needed
        symbols = subprocess.check_output([str(BIN / "llvm-nm"), "-D", "--defined-only", str(path)], text=True)
        for java in (ROOT / "third_party/media3-ffmpeg/java/androidx/media3/decoder/ffmpeg").glob("*.java"):
            for method in re.findall(r"\bnative\s+\w+\s+(\w+)\(", java.read_text()):
                assert f"Java_androidx_media3_decoder_ffmpeg_{java.stem}_{method}" in symbols, method
        print(abi, path.stat().st_size, "bytes; JNI exports/system dependencies verified")
data = {"inputs": provenance(), "libraries": libraries}
manifest = GENERATED / "manifest.json"
if args.record:
    manifest.write_text(json.dumps(data, indent=2) + "\n")
else:
    assert json.loads(manifest.read_text()) == data, "Native source/library drift; rebuild AU-1"
if args.apk:
    with zipfile.ZipFile(args.apk) as apk:
        names = {n for n in apk.namelist() if n.endswith("/libffmpegJNI.so")}
        assert names == {f"lib/{abi}/libffmpegJNI.so" for abi in ABIS}, names
        for abi in ABIS:
            packaged = apk.read(f"lib/{abi}/libffmpegJNI.so")
            check_elf(packaged, abi)
            assert hashlib.sha256(packaged).hexdigest() == libraries[abi]
        assert apk.read("assets/licenses/FFmpeg-LGPL-2.1.txt")
        assert apk.read("assets/licenses/Media3-FFmpeg-Apache-2.0.txt")
    print("APK: both ARM ABIs byte-identical to verified build; license assets present")
print("AU-1 native verification passed")
