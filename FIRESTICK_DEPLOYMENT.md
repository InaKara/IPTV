# Deploy Aypi Tivi to a Firestick

## One command for normal development

Create your private `.env` once (see below), then open PowerShell in this project and run:

```powershell
.\deploy-firestick.ps1
```

The script reads local settings, checks JDK 17, builds a fresh debug APK, connects to the Firestick, installs with `-r` to preserve saved app data, and launches Aypi Tivi. It stops on build, connection or installation failure. Confirm picture, sound and the changed controls on the television after deployment.

Useful options:

```powershell
.\deploy-firestick.ps1 -Check        # Build, unit tests, lint, install and launch
.\deploy-firestick.ps1 -BuildOnly    # Build without touching the Firestick
.\deploy-firestick.ps1 -InstallOnly  # Install/launch the existing APK without rebuilding
```

`-Check` can be combined with `-BuildOnly`. Use `-InstallOnly` only when the APK already contains your latest changes. Installing an APK does not compile source code.

Android Studio is optional for this command-line workflow. If PowerShell blocks the script, allow it for that process only:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy-firestick.ps1
```

## Private settings: one-time setup

```powershell
Copy-Item .env.example .env
```

Edit `.env` locally. Do not replace an existing `.env` just to repeat a deployment.

| Key | Purpose |
| --- | --- |
| IPTV_SERVER_URL | Provider base address; a final slash is accepted and removed |
| IPTV_USERNAME / IPTV_PASSWORD | Default login fields in your local debug build |
| IPTV_LEGACY_SERVER_URLS | Optional comma-separated retired addresses to replace in saved settings |
| FIRESTICK_IP | Firestick's current local IPv4 address |
| FIRESTICK_ADB_PORT | ADB port, normally 5555 |
| JAVA_HOME | Optional path to a JDK 17 installation |
| ANDROID_HOME | Optional path to the Android SDK containing platform-tools/adb.exe |

Use literal `KEY=value` lines. Blank lines and lines beginning with `#` are ignored. Matching single/double quotes around a value are removed. No variable expansion, shell execution, escape processing or inline comments are performed. Use forward slashes in Windows paths. Values containing `=` or `#` are supported.

The script resolves paths relative to itself, so the project can live anywhere. If JAVA_HOME or ANDROID_HOME is blank, existing environment settings are used; ADB can also be found on PATH. The standard local Android SDK directory is a fallback. An existing local.properties file must point to the correct SDK.

The repository ignores `.env`, local.properties, IDE state, caches, builds, APKs, logs and signing keys. `.env.example` contains no real account or device settings.

**Android does not read a computer's .env at runtime.** Gradle reads it when building and generates the debug login defaults. Rebuild and reinstall after changing those defaults. Personal defaults are embedded in that debug APK, so do not distribute it publicly. Release builds have empty login defaults, even when `.env` exists. A checkout without `.env` also builds with empty defaults.

Saved device credentials take precedence over defaults. Changing the .env username/password does not overwrite credentials already saved on the Firestick. For a provider move, optionally list the old base address in IPTV_LEGACY_SERVER_URLS; other custom addresses are preserved. Never clear app data merely to change a default.

## Tool and Firestick setup: only when needed

On the computer, install JDK 17, Android SDK Platform 34, Build Tools 34.0.0 and Platform Tools. Use the repository's Gradle 8.2 wrapper and Android Gradle plugin 8.2.2. If using Android Studio, select JDK 17 under **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK**. Android Studio's own runtime can remain Java 21.

On the Firestick:

1. Connect the computer and Firestick to the same local network.
2. Find its IP under **Settings > My Fire TV > About > Network** and put it in `.env`.
3. Enable **Settings > My Fire TV > Developer Options > ADB Debugging**.
4. If Developer Options is hidden, highlight the device name under About and press Select seven times.
5. Approve the computer's debugging prompt on the TV when connecting.

Repeat setup only after changing computers, reinstalling tools, resetting the Firestick, or diagnosing a specific setup error. Recheck FIRESTICK_IP if connection fails or your network changes. Preserve the original debug signing key on your computer for app updates.

## Troubleshooting

- **Java/class-file error:** point JAVA_HOME in `.env` to JDK 17. Use the same Gradle JDK in Android Studio.
- **SDK not found:** check ANDROID_HOME and local.properties. SDK Platform 34 and Build Tools 34.0.0 must be installed.
- **Connection/authorization failure:** check IP, network, ADB Debugging and the prompt on the TV. The script will not uninstall anything to recover.
- **INSTALL_FAILED_UPDATE_INCOMPATIBLE:** signing keys differ. Find the original key. Uninstalling would remove saved app data.
- **INSTALL_FAILED_VERSION_DOWNGRADE:** inspect versionCode before choosing a versioning change.
- **Storage error:** free space through Fire TV settings.
- **Login failure:** check provider availability, the base server address and account access. Login downloads an M3U playlist, not a full Xtream API session.
- **Playback failure:** inspect stream format and player error. Current dependencies lack HLS/DASH/RTMP extension modules, and parsed user-agent metadata is not applied to playback.

To inspect recent errors with your own SDK/device values:

```powershell
adb -s '<firestick-ip>:5555' logcat -b crash -d -t 200
adb -s '<firestick-ip>:5555' logcat -d -t 500 'AndroidRuntime:E' 'LoginViewModel:D' 'PlayerViewModel:D' '*:S'
```

Use the full path to adb.exe if it is not on PATH. The app has its own exception screen, so an empty crash buffer does not rule out failure. Logs and crash messages can contain provider details or authenticated URLs; redact before sharing and keep logs out of Git.

## Verification record

On 21 October 2026, the manual debug build and installation succeeded and the user reported that the app works. The device runs Android 9 / API 28, meeting the app minimum of API 26. That recovery included the login keyboard handler and provider-default update. Individual VOD/keyboard/relaunch checks were not separately reported.

The subsequent .env/deployment-script change was also verified:

- PowerShell syntax, literal .env parsing and native-command failure handling passed.
- The script's -BuildOnly -Check run passed the debug build, parser unit test and Android lint (0 errors / 19 existing warnings).
- Generated debug defaults matched the private .env, including removal of the URL's final slash.
- Release defaults were verified empty with .env present, and a debug build without .env passed with empty defaults.
- A normal script run rebuilt with local defaults, installed successfully on the Firestick and received a successful activity launch. Playback after this particular update has not been separately reported.
- The public source tree was checked for the known private values; .env, generated outputs and local tool settings are excluded. Publication uses a fresh initial commit rather than the old history containing private defaults.

No library migration is part of this change.

## References

- [Amazon: connect to Fire TV through ADB](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html)
- [Android Gradle plugin 8.2 compatibility](https://developer.android.com/build/releases/agp-8-2-0-release-notes)
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
