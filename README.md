# Aypi Tivi

A personal IPTV player for Android TV and Amazon Fire TV Stick, written in Kotlin with Jetpack Compose for TV and AndroidX Media3 ExoPlayer. The intended device is a Fire TV Stick 4K Max. The app downloads channels from the user's provider; it supplies no content.

## Requirements and review

[REQUIREMENTS.md](REQUIREMENTS.md) is the requirement catalog, grouped by functionality with stable IDs, acceptance criteria, implementation statuses and evidence. Its initial retrospective version is **awaiting user review**; an Implemented status describes the baseline and does not imply individual device acceptance or approval of future changes.

For every new wish, update the catalog first and present the affected IDs and changes for review. **Wait for the user's explicit approval of that scope before implementation.** Then implement the selected requirements, validate them and update their statuses/evidence. Approval of the initial catalog does not automatically authorize development of every unresolved entry. Scope changes go back through catalog review.

## Code-review remediation

The user authorized all 35 review suggestions for implementation. [CODE_REVIEW_FIXES.md](CODE_REVIEW_FIXES.md) maps each finding to the remediation and its checks; that source baseline was committed/pushed as `ec11bdc`. The initial catalog's unrelated product decisions still await review. Latest launcher, local-logo, startup-timing and identity-migration device checks are recorded in the deployment guide; playback and other unchecked criteria remain separate.

## Current app

1. A two-second splash screen shows the classic logo and Aypi Tivi name.
2. Login accepts a server address, username and password. Connect receives initial focus. Successful connections save the fields on the device. The playlist is downloaded again on subsequent connections.
3. Categories and Channels share a dark tile browser with a persistent four-icon sidebar: Categories, Favorites, Search and Settings. Recently viewed appears first (up to 20 entries), followed by TR categories and other categories alphabetically. Select a category to see its channels; Back restores the category grid. Empty recents remain available without filler channels.
4. The default grid fits five columns and four rows. Settings offers Large (4 x 3), Standard (5 x 4) and Compact (6 x 4), saved across launches. Amber fill marks focus without enlarging controls; the active sidebar destination has its own quieter indicator.
5. Menu on a focused channel opens Add/Remove Favorite; keyboard Context Menu or Shift+F10 opens the same action. Favorites persist per account and appear in playlist order. Back from Favorites restores the preceding Categories/category-channel view. Search matches channel names from the loaded playlist and returns to the previous view when closed.
6. Selecting a channel opens ExoPlayer. Leaving the player stops/releases playback and restores the originating view, focus and scroll; no stream runs beneath browsing. Back at Categories root offers the existing exit confirmation. Channel cards display an optional provider `tvg-chno` number without invented numbering.

The server field is a base address such as `https://provider.example:8080`, without a trailing slash or `/get.php` query. A missing scheme now defaults to HTTPS. Explicit HTTP is retained with an on-screen encryption warning. Requests never silently downgrade HTTPS. The repository encodes each query parameter:

```text
<base-address>/get.php?username=<username>&password=<password>&type=m3u_plus
```

This is an M3U download endpoint in Xtream-style URL format, not a full Xtream API integration. Parsing preserves commas in names and ignores URL queries/fragments during VOD detection. Coil caches channel logos, including SVGs through its matching 2.5.0 decoder extension. Missing, blank, failed and unsupported logos use one centered gray play-symbol fallback. Per-channel user-agent metadata reaches playback, alongside HLS/DASH and progressive HTTP(S) support. RTMP is not claimed supported.

Connecting includes the entire streaming playlist download/parser span and recent-history preparation. Loading channels then groups the downloaded list. Their individual screen durations are not an end-to-end speed measurement. Debug builds report private-data-free phase timings under the `AypiStartup` log tag, ending at the first browsing-list layout; release builds disable these diagnostics. See [UX_INVESTIGATION.md](UX_INVESTIGATION.md) for measurements and their limits.

The future startup-directly-to-channel-list flow is recorded as LOGIN-008. Its download/cache/refresh policy is undecided and it is not implemented in this change. Current diagnostics remain reusable; no optimization specific to manual login was introduced.

Default login fields come from a private local `.env` at build time. Optional retired server addresses in that file can replace old saved addresses; other custom addresses and saved credentials are preserved. Release builds and checkouts without `.env` have empty defaults. Remote Select/Enter explicitly requests the keyboard on a focused login field. Physical-device confirmation of this recovery update is recorded in the deployment guide.

### Remote controls implemented in code

| Context / button | Action |
| --- | --- |
| Login / D-pad and Select | Move between fields and Connect; select to edit |
| Categories / Select | Activate category |
| Sidebar / Up or Down | Move among its four icons without activating them |
| Sidebar / Right | Restore the last focused grid item; empty grids keep usable sidebar focus |
| Grid / Arrows | Move by column/row, without wrapping; scroll further rows into view |
| Grid / Left from leftmost column | Focus the active sidebar destination |
| Channel / Menu or Context Menu / Shift+F10 | Open Add/Remove Favorite |
| Channel grid / Back | Restore Categories with its focus/scroll |
| Player / Up or Channel Up | Next channel in the current category |
| Player / Down or Channel Down | Previous channel in the current category |
| Player / Select or Play/Pause | Toggle playback |
| Player / Left or Right on detected VOD | Seek backward or forward 30 seconds |
| Player / Rewind or Fast Forward | Seek backward or forward 10 seconds |
| Player / Back | Return to browsing |

These are code mappings, not a completed device acceptance test. All seeking, including native controller seeking, requires detected VOD, stream seekability and a known positive duration; positions clamp to its bounds. Backgrounding releases the player. Returning restores VOD position and previous play/pause intent, while live playback returns to its current stream. VOD detection uses category keywords, URL paths and file extensions. Playback quality, including 4K, depends on the stream, codec, device and connection.

## Build setup

The repository pins these versions. An updated Android Studio installation does not require updating all app dependencies.

| Component | Project version |
| --- | --- |
| Android Gradle plugin | 8.2.2 |
| Gradle / build runtime | 8.2 / JDK 17 |
| Kotlin / Compose compiler | 1.9.22 / 1.5.8 |
| Compose BOM / TV Foundation and Material | 2024.02.02 / 1.0.0-alpha10 |
| Lint runtime override | 8.3.2 (artifact 31.3.2) |
| Media3 / Hilt | 1.2.1 / 2.50 |
| OkHttp / Coil / Gson | 4.12.0 / 2.5.0 / 2.10.1 |
| Robolectric (test only) | 4.12.2; native rendering on Windows |
| Minimum / compile / target SDK | 26 / 34 / 34 |
| SDK Build Tools | 34.0.0 |

Use JDK 17 to run Gradle; the Java/Kotlin bytecode target is separately set to 1.8. Gradle 8.2 cannot run on Java 21. In Android Studio, select JDK 17 under **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK** before syncing. Keep the project's Gradle wrapper selected. Install Android SDK Platform 34, Build Tools 34.0.0 and Platform Tools through SDK Manager if missing.

## Local configuration and deployment

Copy `.env.example` to `.env` and fill in your provider, Firestick and optional SDK/JDK paths. The real `.env` is ignored. Keep public examples and commits free of personal settings.

In PowerShell, from the project folder:

```powershell
.\deploy-firestick.ps1
```

This builds, connects, updates the Firestick without clearing app data, and launches the app. Add `-Check` for environment fixtures, regression tests (including simulated remote focus) and Android lint, or use `-BuildOnly` / `-InstallOnly` as needed. See [FIRESTICK_DEPLOYMENT.md](FIRESTICK_DEPLOYMENT.md) for setup and troubleshooting.

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

Screens use ViewModels and StateFlow. SessionViewModel retains the playlist and explicit selected position across activity recreation; process death returns to the saved login form. Recent history stores versioned, account-scoped stream references resolved against the current playlist, with JSON work off the UI thread. There is one application module, `app`; the installed application ID is `com.inakara.aypitivi`, while the Kotlin namespace remains `com.example.iptv`. Launch through `com.inakara.aypitivi/com.example.iptv.SplashActivity`, because MainActivity is not exported. OPS-006 moves away from the old installation identity to resolve its incorrect Fire TV tile; see the deployment guide for the one-time private settings/history migration and current device evidence. Ordinary updates still use the same deployment command and preserve the new installation's saved data.

## Validation and known limitations

Run the quick local regression suite after each implementation:

```powershell
.\test-smoke.ps1
.\test-smoke.ps1 -Lint  # Also run Android lint
```

The command uses the private JDK/SDK paths from .env (or existing environment settings), runs fresh app tests, both sets of environment fixtures and reporting/project checks, then prints **X/Y passed** with failed/skipped/error counts. It does not install anything or contact your IPTV provider. Tests use synthetic data and a loopback fake server; first-time Gradle/dependency downloads still need network access. Compilation remains incremental while test execution is fresh. Use JDK 17 as documented above.

Every requirement in [REQUIREMENTS.md](REQUIREMENTS.md) has a generated companion row listing testcase IDs, descriptions and latest results. [TEST_CASES.md](TEST_CASES.md) provides the complete testcase view; [scripts/test-catalog.json](scripts/test-catalog.json) owns stable IDs, selectors, coverage gaps and requirement-definition/status fingerprints. **Passed tests do not imply that every acceptance criterion or physical-device behavior is verified.** Manual cases remain Not run in this local suite; partial and superseded requirements retain their distinctions.

After changing a requirement, first update its actual tests and mappings as appropriate (add, modify or retire cases). Explicitly review retained cases, then acknowledge the affected fingerprints with scripts/Update-TestMapping.ps1 and a specific review note. The runner rejects missing/stale mappings, missing or unexpected tests, skipped checks and failed builds. Generated result/timestamp changes do not require new tests. Future coding work follows this process automatically through AGENTS.md.

The target is high useful coverage within approximately **90 seconds on a warm setup**, rather than 100% coverage. The script prints elapsed time; build setup, first-time downloads and optional lint may take longer. Real playback, keyboard/decoder behavior, 4K, provider compatibility, signing and device acceptance remain separate checks.

Detailed JSON and private logs are under the ignored app/build/reports/smoke/ directory, with a latest.json summary and a separate directory for each run. Keep raw logs local. Failure returns a nonzero exit code and updates corresponding catalog results; it never labels a missing result as Passed. Build/lint results are not added to behavioral test counts.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Regression checks cover parsing, URL encoding, connection reuse/cancellation, login concurrency, history/schema migration, seek bounds, channel position and Compose focus using synthetic data. Image checks decode local PNG/SVG fixtures, exercise memory/disk caches, compare missing/failed fallback pixels and render application/activity branding. Startup checks cover phase ordering through the first browsing layout, duration arithmetic, safe output and retry/cancellation. Native rendering may download an additional test runtime on first use. Physical-device checks are essential for launcher thumbnails, D-pad focus, playback and resume behavior. See the deployment guide's verification record for the latest check results.

- HLS/DASH modules and per-channel user-agent support are included; provider/codec compatibility and 4K still require device tests. RTMP requires separately selected support.
- Saved credentials and stream references remain in unencrypted app-private preferences, excluded from backup/transfer. Explicit HTTP exposes credentials in transit; prefer verified provider HTTPS when available. App-controlled errors omit URLs/stacks; Android/platform diagnostics may still be private.
- Empty/scrolled focus targets have regression checks; physical remote and keyboard acceptance remain pending. The login form scrolls to keep controls reachable on smaller screens.
- Release builds enable R8/resource shrinking and have empty defaults. The generated release APK is unsigned; signing/distribution and minified device acceptance remain separate.
- Generated build files, Gradle caches, IDE files and local settings are excluded from public history. Local debug APKs and caches can contain personal defaults; do not upload them.

## Development approach

The deployment baseline works. Development follows the catalog-first review process in [REQUIREMENTS.md](REQUIREMENTS.md). Keep requirement IDs traceable in implementation summaries and commits/PRs when created. After scope approval, make small, independently checked changes, keep the deployment guide current, and verify relevant remote navigation and playback on the Firestick. Record what was actually checked and what remains unresolved.

See [AGENTS.md](AGENTS.md) for repository-specific guidance for coding assistants.

## References

- [Android Gradle plugin 8.2 compatibility](https://developer.android.com/build/releases/agp-8-2-0-release-notes)
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
- [Amazon: connect to Fire TV through ADB](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html)
