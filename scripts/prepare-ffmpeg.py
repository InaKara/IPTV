"""Prepare pinned AU-1 sources and Linux NDK inside the ignored workspace.

Run with Python 3.12+ under Linux/WSL; no global SDK/package changes.
"""
import hashlib
import json
import pathlib
import shutil
import stat
import tarfile
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
WORK = ROOT / ".private/ffmpeg-build"
WORK.mkdir(parents=True, exist_ok=True)
lock = json.loads((ROOT / "scripts/ffmpeg-sources.json").read_text())
for name, item in lock.items():
    target = WORK / item["file"]
    if not target.exists():
        partial = target.with_suffix(target.suffix + ".part")
        with urllib.request.urlopen(item["url"], timeout=60) as src, partial.open("wb") as dst:
            shutil.copyfileobj(src, dst, 1024 * 1024)
        partial.replace(target)
    with target.open("rb") as src:
        actual = hashlib.file_digest(src, "sha256").hexdigest()
    if actual != item["sha256"]:
        raise RuntimeError(f"Checksum mismatch: {name}; remove the incorrect archive and retry")
    print(f"Verified {name}", flush=True)

for name in ("ffmpeg", "media3"):
    destination = WORK / name
    marker = destination / ".complete"
    if marker.exists():
        continue
    destination.mkdir(exist_ok=True)
    with tarfile.open(WORK / lock[name]["file"]) as archive:
        members = archive.getmembers()
        if name == "media3":
            members = [m for m in members if "/libraries/decoder_ffmpeg/" in m.name or m.name.endswith("/LICENSE")]
        archive.extractall(destination, members=members, filter="data")
    marker.touch()

destination = WORK / "ndk"
destination.mkdir(exist_ok=True)
marker = destination / ".complete"
if not marker.exists():
    with zipfile.ZipFile(WORK / lock["ndk"]["file"]) as archive:
        for item in archive.infolist():
            if not (item.filename.startswith("android-ndk-r26b/toolchains/llvm/prebuilt/linux-x86_64/")
                    or item.filename.startswith("android-ndk-r26b/prebuilt/linux-x86_64/bin/make")
                    or item.filename == "android-ndk-r26b/source.properties"):
                continue
            target = destination.joinpath(*pathlib.PurePosixPath(item.filename).parts)
            if not target.resolve().is_relative_to(destination.resolve()):
                raise RuntimeError("Unsafe NDK path")
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            mode = item.external_attr >> 16
            if stat.S_ISLNK(mode):
                link = archive.read(item).decode()
                if not (target.parent / link).resolve().is_relative_to(destination.resolve()):
                    raise RuntimeError("Unsafe NDK link")
                if not target.is_symlink():
                    target.symlink_to(link)
            elif not target.exists() or target.stat().st_size != item.file_size:
                with archive.open(item) as src, target.open("wb") as dst:
                    shutil.copyfileobj(src, dst, 1024 * 1024)
                target.chmod(mode & 0o777 or 0o644)
    marker.touch()

# Vendored Java/JNI files must remain identical to this version of official Media3.
upstream = WORK / "media3/media-1.2.1/libraries/decoder_ffmpeg/src/main"
vendor = ROOT / "third_party/media3-ffmpeg"
for source in (upstream / "java").rglob("*.java"):
    relative = source.relative_to(upstream / "java")
    if source.read_bytes() != (vendor / "java" / relative).read_bytes():
        raise RuntimeError(f"Vendored source differs: {relative}")
if (upstream / "jni/ffmpeg_jni.cc").read_bytes() != (vendor / "jni/ffmpeg_jni.cc").read_bytes():
    raise RuntimeError("Vendored JNI differs from pinned upstream")
print("AU-1 sources/toolchain ready", flush=True)
