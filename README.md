# Aypi Tivi

A personal IPTV player for Android TV and Amazon Fire TV Stick, written in Kotlin with Jetpack Compose for TV and AndroidX Media3 ExoPlayer. The intended device is a Fire TV Stick 4K Max. The app downloads channels from the user's provider; it supplies no content.

## Requirements and review

[REQUIREMENTS.md](REQUIREMENTS.md) is the requirement catalog, grouped by functionality with stable IDs, acceptance criteria, implementation statuses and evidence. Its initial retrospective version is **awaiting user review**; an Implemented status describes the baseline and does not imply individual device acceptance or approval of future changes.

For every new wish, update the catalog first and present the affected IDs and changes for review. **Wait for the user's explicit approval of that scope before implementation.** Then implement the selected requirements, validate them and update their statuses/evidence. Approval of the initial catalog does not automatically authorize development of every unresolved entry. Scope changes go back through catalog review.

## Current app

1. A two-second splash screen shows the classic logo and Aypi Tivi name.
2. Login accepts a server address, username and password. Connect receives initial focus. Successful connections save the fields on the device. The playlist is downloaded again on subsequent connections.
3. A persistent category sidebar sits beside a four-column channel grid. Recently viewed appears first (up to 20 entries), followed by categories beginning with TR, then other categories alphabetically. A new installation's recent category is empty; select another category to find channels.
4. Selecting a channel opens ExoPlayer. Back returns to browsing. Back from browsing offers an exit confirmation.

The server field is a base address such as `https://provider.example:8080`, without a trailing slash or `/get.php` query. The repository constructs:

```text
<base-address>/get.php?username=<username>&password=<password>&type=m3u_plus
```

This is an M3U download endpoint in Xtream-style URL format, not a full Xtream API integration. Parsing extracts names, groups, logo URLs, TVG metadata, user-agent metadata and a heuristic VOD flag. Coil loads channel logos with a disk cache.

Default login fields come from a private local `.env` at build time. Optional retired server addresses in that file can replace old saved addresses; other custom addresses and saved credentials are preserved. Release builds and checkouts without `.env` have empty defaults. Remote Select/Enter explicitly requests the keyboard on a focused login field. Physical-device confirmation of this recovery update is recorded in the deployment guide.

### Remote controls implemented in code

| Context / button | Action |
| --- | --- |
| Login / D-pad and Select | Move between fields and Connect; select to edit |
| Categories / Select | Activate category |
| Categories / Right | Request focus on the first channel |
| Grid / Left from leftmost column | Request focus on the active category |
| Player / Up or Channel Up | Next channel in the current category |
| Player / Down or Channel Down | Previous channel in the current category |
| Player / Select or Play/Pause | Toggle playback |
| Player / Left or Right on detected VOD | Seek backward or forward 30 seconds |
| Player / Rewind or Fast Forward | Seek backward or forward 10 seconds |
| Player / Back | Return to browsing |

These are code mappings, not a completed device acceptance test. Seeking depends on the stream. VOD detection uses category keywords, URL paths and file extensions. Playback quality, including 4K, depends on the stream, codec, device and connection.

## Build setup

The repository pins these versions. An updated Android Studio installation does not require updating all app dependencies.

| Component | Project version |
| --- | --- |
| Android Gradle plugin | 8.2.2 |
| Gradle / build runtime | 8.2 / JDK 17 |
| Kotlin / Compose compiler | 1.9.22 / 1.5.8 |
| Compose BOM / TV Foundation and Material | 2024.01.00 / 1.0.0-alpha10 |
| Media3 / Hilt | 1.2.1 / 2.50 |
| OkHttp / Coil / Gson | 4.12.0 / 2.5.0 / 2.10.1 |
| Minimum / compile / target SDK | 26 / 34 / 34 |
| SDK Build Tools | 34.0.0 |

Use JDK 17 to run Gradle; the Java/Kotlin bytecode target is separately set to 1.8. Gradle 8.2 cannot run on Java 21. In Android Studio, select JDK 17 under **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK** before syncing. Keep the project's Gradle wrapper selected. Install Android SDK Platform 34, Build Tools 34.0.0 and Platform Tools through SDK Manager if missing.

## Local configuration and deployment

Copy `.env.example` to `.env` and fill in your provider, Firestick and optional SDK/JDK paths. The real `.env` is ignored. Keep public examples and commits free of personal settings.

In PowerShell, from the project folder:

```powershell
.\deploy-firestick.ps1
```

This builds, connects, updates the Firestick without clearing app data, and launches the app. Add `-Check` to run the parser tests and Android lint, or use `-BuildOnly` / `-InstallOnly` as needed. See [FIRESTICK_DEPLOYMENT.md](FIRESTICK_DEPLOYMENT.md) for setup and troubleshooting.

Gradle reads `.env` at build time; Android does not load it from the computer at runtime. Local debug APKs include those defaults and must stay private. Release defaults stay empty. Saved credentials on the device override defaults. Build output, local SDK settings, IDE state, logs and signing material are ignored by Git.

The APK is `app/build/outputs/apk/debug/app-debug.apk`. A fresh public checkout builds without `.env` using blank login fields; configure an Android SDK and JDK 17 first.

## Source map

```text
app/src/main/java/com/example/iptv/
  IPTVApplication.kt       Hilt, Coil, exception handler
  SplashActivity.kt        Exported launcher activity
  MainActivity.kt          Login/browse/player state and recent history
  data/model/              Channel and category types
  data/parser/             M3U input-stream parser and VOD detection
  data/repository/         HTTP playlist download
  data/storage/            Credentials and recent channels in SharedPreferences
  di/AppModule.kt          Dependency providers
  ui/login/                Login form and connection state
  ui/home/                 Category sidebar and channel grid
  ui/player/               ExoPlayer lifecycle, controls, overlays
  ui/error/                Crash display
  ui/theme/                Dark theme with blue accent
  util/                    Global exception handler
```

Screens use ViewModels and StateFlow. Top-level navigation is held in Compose state in MainActivity. There is one application module, `app`; the application ID is `com.example.iptv`. Launch through `com.example.iptv/.SplashActivity`, because MainActivity is not exported.

## Validation and known limitations

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

One parser unit-test file exists and uses the parser's InputStream API. Physical-device checks are essential for D-pad focus, playback and resume behavior. See the deployment guide's verification record for the latest check results.

- The declared Media3 dependencies include the core player and UI, but no HLS, DASH or RTMP extension modules. Do not assume those protocols work from the old README or conversation; inspect the actual stream and add the appropriate module in a separate development change if needed.
- Playlist user-agent metadata is parsed but not passed into the player. Provider-specific requirements may affect playback.
- Local debug defaults are generated from the ignored `.env`. Saved credentials use unencrypted app-private SharedPreferences. Stream URLs and connection details can appear in debug logs. Keep credentials and raw logs out of shared documentation and commits. The manifest allows HTTP traffic.
- Category-to-grid focus requests can target an absent item when a category is empty or its first item is not composed. Diagnose this if D-pad navigation crashes.
- Generated build files, Gradle caches, IDE files and local settings are excluded from public history. Local debug APKs and caches can contain personal defaults; do not upload them.

## Development approach

The deployment baseline works. Development follows the catalog-first review process in [REQUIREMENTS.md](REQUIREMENTS.md). Keep requirement IDs traceable in implementation summaries and commits/PRs when created. After scope approval, make small, independently checked changes, keep the deployment guide current, and verify relevant remote navigation and playback on the Firestick. Record what was actually checked and what remains unresolved.

See [AGENTS.md](AGENTS.md) for repository-specific guidance for coding assistants.

## References

- [Android Gradle plugin 8.2 compatibility](https://developer.android.com/build/releases/agp-8-2-0-release-notes)
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
- [Amazon: connect to Fire TV through ADB](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html)
