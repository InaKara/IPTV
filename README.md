# Aypi Tivi

A personal IPTV player for Android TV and Amazon Fire TV Stick, written in Kotlin with Jetpack Compose for TV and AndroidX Media3 ExoPlayer. The intended device is a Fire TV Stick 4K Max. The app downloads channels from the user's provider; it supplies no content.

## Requirements and review

[REQUIREMENTS.md](docs/requirements/REQUIREMENTS.md) is the requirement catalog, grouped by functionality with stable IDs, acceptance criteria, implementation statuses and evidence. Its initial retrospective version is **awaiting user review**; an Implemented status describes the baseline and does not imply individual device acceptance or approval of future changes.

For every new wish, update the catalog first and present the affected IDs and changes for review. **Wait for the user's explicit approval of that scope before implementation.** Then implement the selected requirements, validate them and update their statuses/evidence. Approval of the initial catalog does not automatically authorize development of every unresolved entry. Scope changes go back through catalog review.

Documentation lives under `docs/`: [backlog](docs/backlog.md), [requirements](docs/requirements/REQUIREMENTS.md), [generated test cases](docs/requirements/TEST_CASES.md), [deployment guide](docs/operations/FIRESTICK_DEPLOYMENT.md), design reviews in `docs/design/`, investigations in `docs/investigations/` and historical code reviews in `docs/reviews/`. Commands run from repository root. Only README.md and AGENTS.md remain as root Markdown files. Backlog entries grant no implementation or deployment approval.

## Code-review remediation

The user authorized all 35 review suggestions for implementation. [CODE_REVIEW_FIXES.md](docs/reviews/CODE_REVIEW_FIXES.md) maps each finding to the remediation and its checks; that source baseline was committed/pushed as `ec11bdc`. The initial catalog's unrelated product decisions still await review. Latest launcher, local-logo, startup-timing and identity-migration device checks are recorded in the deployment guide; playback and other unchecked criteria remain separate.

## Current app

1. A two-second splash screen shows the classic logo and Aypi Tivi name.
2. Startup automatically loads the current account's saved channel list, keeping the classic logo/name visible with status underneath through connection and list preparation. A fresh list needs no download; one more than 24 hours old appears before a background refresh starts. With no usable snapshot, complete account settings trigger an automatic download. Missing settings open Settings setup; initial errors offer Open Settings for retry/edit. Load/refresh controls stay in Settings.
3. The persistent sidebar shows Live TV (screen/default), VOD (film), Favorites and Search, with Settings at the bottom. Fresh browsing focuses the screen icon. Live TV and VOD each show provider-named categories with matching entries/counts. Recently viewed appears first in each section, filtered from the same shared 20-entry history; remaining categories retain TR-first/alphabetical order. Select a category to see its entries; Back restores that section's category grid. Empty recents remain available without filler channels.
4. The default grid fits five columns and four rows. The fixed heading shows Live TV/VOD at category roots, the opened category name inside a group, or Favorites/Search/Settings for those destinations. Long headings stay on one line with an ellipsis; blank category names use Channels. Settings offers saved Large (4 x 3), Standard (5 x 4) and Compact (6 x 4) density, the adult-content filter, account fields, Save and load, Refresh channel list and the last successful download time. Account edits are saved only after their list loads successfully. Amber fill marks focus without enlarging controls; the active sidebar destination has its own quieter indicator.
5. Menu on a focused channel opens Add/Remove Favorite; keyboard Context Menu or Shift+F10 opens the same action. Favorites persist per account and appear in playlist order across both types. Back from Favorites restores the preceding Live TV/VOD root or category view. Search matches names across the loaded live/VOD playlist and returns to the previous view when closed. Live TV and VOD keep separate root/category focus and scroll positions.
6. Selecting a channel/item opens ExoPlayer, with stepping confined to the selected collection. Leaving the player stops/releases playback and restores the originating section/view, focus and scroll; no stream runs beneath browsing. Back at either category root offers the existing exit confirmation. Channel cards display an optional provider `tvg-chno` number without invented numbering.

The server field is a base address such as `https://provider.example:8080`, without a trailing slash or `/get.php` query. A missing scheme now defaults to HTTPS. Explicit HTTP is retained with an on-screen encryption warning. Requests never silently downgrade HTTPS. The repository encodes each query parameter:

```text
<base-address>/get.php?username=<username>&password=<password>&type=m3u_plus
```

This is an M3U download endpoint in Xtream-style URL format, not a full Xtream API integration. Parsing preserves commas in names and ignores URL queries/fragments during VOD detection. Coil caches channel logos, including SVGs through its matching 2.5.0 decoder extension. Missing, blank, failed and unsupported logos use one centered gray play-symbol fallback. Per-channel user-agent metadata reaches playback, alongside HLS/DASH and progressive HTTP(S) support. RTMP is not claimed supported.

Connecting covers the streaming playlist download/parser span; Loading channels covers remaining preparation/grouping. These are short statuses below the startup logo, not separate forms. Their durations are not an end-to-end speed measurement. Debug builds report private-data-free phase timings under the `AypiStartup` log tag, ending at the first browsing-list layout; release builds disable these diagnostics. See [UX_INVESTIGATION.md](docs/investigations/UX_INVESTIGATION.md) for measurements and their limits.

Channel artwork fits the remaining tile space at each density without cropping or stretching. In playback, Menu reveals native controls with amber focus; arrows navigate while controls are visible, OK activates the focused control and Back hides them first. Controls hide after three seconds without interaction. Debug-only `AypiAudio` records use fixed codec/event names and counts to distinguish missing, unsupported and selected audio tracks without provider identifiers or raw exception messages. No software audio decoder is bundled; the MP2 follow-up in [LATEST_APP_REVIEW.md](docs/design/LATEST_APP_REVIEW.md) is a separate proposal.

The approved automatic startup/cache/settings flow is specified in [STARTUP_REFRESH_REVIEW.md](docs/design/STARTUP_REFRESH_REVIEW.md). Snapshots are versioned, account scoped, atomically replaced off the UI thread and excluded from backup. Failed refreshes retain the working list and its age; successful refreshes preserve browser context and current playback. Freshness is checked at startup and foreground return, without a downloader running while the app is closed. Playback uses Android's keep-screen-on facility while playing or buffering with playback requested, clearing it on pause/end/error/Back/background. Local checks cover these paths. SF-1 also verifies real-provider first load and a fresh cached restart to Categories on Firestick (23 October); blank optional categories no longer reject the list. Other device acceptance, long screensaver tests and the earlier memory-exhaustion issue remain separate. See [STARTUP_FAILURE_INVESTIGATION.md](docs/investigations/STARTUP_FAILURE_INVESTIGATION.md).

Default login fields come from a private local `.env` at build time. Optional retired server addresses in that file can replace old saved addresses; other custom addresses and saved credentials are preserved. Release builds and checkouts without `.env` have empty defaults. Remote Select/Enter explicitly requests the keyboard on a focused login field. Physical-device confirmation of this recovery update is recorded in the deployment guide.

### Remote controls implemented in code

| Context / button | Action |
| --- | --- |
| Settings account / D-pad and Select | Move between fields and actions; select a field to edit |
| Live TV or VOD categories / Select | Activate category |
| Sidebar / Up or Down | Move among its five icons without activating them |
| Sidebar / Right | Restore the last focused grid item; empty grids keep usable sidebar focus |
| Grid / Arrows | Move by column/row, without wrapping; scroll further rows into view |
| Grid / Left from leftmost column | Focus the active sidebar destination |
| Channel / Menu or Context Menu / Shift+F10 | Open Add/Remove Favorite |
| Channel grid / Back | Restore the matching Live TV/VOD category root with its focus/scroll |
| Player / Menu | Reveal controls and focus Play/Pause |
| Visible player controls / Arrows and OK | Move focus and activate the focused control |
| Hidden player controls / Up or Down | Next or previous channel in the current category |
| Player / Channel Up or Channel Down | Next or previous channel in the current category |
| Hidden player controls / Select | Toggle playback |
| Player / Play/Pause, Play or Pause | Perform the named media action |
| Hidden player controls / Left or Right on detected VOD | Seek backward or forward 30 seconds |
| Player / Rewind or Fast Forward | Seek backward or forward 10 seconds |
| Player / Back | Hide visible controls first; when hidden, return to browsing |

These are code mappings, not a completed device acceptance test. All seeking, including native controller seeking, requires detected VOD, stream seekability and a known positive duration; positions clamp to its bounds. Backgrounding releases the player. Returning restores VOD position and previous play/pause intent, while live playback returns to its current stream. VOD detection uses category keywords, URL paths and file extensions. Playback quality, including 4K, depends on the stream, codec, device and connection.

The Live TV/VOD category split uses that existing per-entry detection; it does not obtain an authoritative content type from a structured provider API. A mixed provider category appears in both sections with only matching entries. Favorites/history identities, saved snapshots and account settings are retained. See [LIVE_VOD_BROWSER_REVIEW.md](docs/design/LIVE_VOD_BROWSER_REVIEW.md) for LV-1's accepted behavior and validation gaps.

Settings **Filter adult content** starts with **Filtering active** and saves one device-wide choice across launches and accounts. It hides categories whose names contain `adult`, ignoring capitalization (including `Adults`), and their entries in both sections, Recently viewed, Favorites, Search and playback collections opened from browsing. Matching uses category names only. The full saved list, favorites and shared 20-entry history are retained; switching filtering off restores eligible entries immediately without a download. If the previous category becomes hidden, closing Settings returns safely to that section's category root. Move down from Compact to reach the switch and press Select to toggle it. See [CATEGORY_FILTER_REVIEW.md](docs/design/CATEGORY_FILTER_REVIEW.md).

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

**Your deployment rule:** the agent must ask for your approval before deploying and explain that the script will install, force-stop and start Aypi Tivi. One explicit approval of that combined operation covers that invocation. After approval, it runs only `deploy-firestick.ps1` and reports the result, without extra flags or device operations unless requested. Any other app start, including for tests, requires approval first so your Firestick use is not interrupted. Implementation approval alone does not authorize deployment or launching.

The script's existing build/connect/install/force-stop/start behavior is retained. You withdrew DP-1's proposed removal before implementation. `-BuildOnly` remains local. Optional `-Check` / `-InstallOnly` are available when you specifically request them; see [FIRESTICK_DEPLOYMENT.md](docs/operations/FIRESTICK_DEPLOYMENT.md).

Gradle reads `.env` at build time; Android does not load it from the computer at runtime. Local debug APKs include those defaults and must stay private. Release defaults stay empty. Saved credentials on the device override defaults. Build output, local SDK settings, IDE state, logs and signing material are ignored by Git.

The APK is `app/build/outputs/apk/debug/app-debug.apk`. A fresh public checkout builds without `.env` using blank login fields; configure an Android SDK and JDK 17 first.

## Source map

```text
app/src/main/java/com/example/iptv/
  IPTVApplication.kt       Hilt, Coil, exception handler
  SplashActivity.kt        Exported launcher activity
  MainActivity.kt          Automatic startup, Settings/browse/player routing
  data/model/              Channel and category types
  data/parser/             M3U input-stream parser and VOD detection
  data/repository/         HTTP playlist download
  data/storage/            Preferences/history and private atomic channel snapshots
  di/AppModule.kt          Dependency providers
  ui/login/                Settings account form and startup/refresh coordination
  ui/home/                 Icon sidebar, categories/channels and Settings layout
  ui/player/               ExoPlayer lifecycle, controls, overlays
  ui/error/                Crash display
  ui/theme/                Dark theme with blue accent
  util/                    Global exception handler
```

Screens use ViewModels and StateFlow. SessionViewModel retains the playlist and explicit selected position across activity recreation; process death restarts automatic loading from the current account's snapshot. Recent history stores versioned, account-scoped stream references resolved against the current playlist, with JSON work off the UI thread. There is one application module, `app`; the installed application ID is `com.inakara.aypitivi`, while the Kotlin namespace remains `com.example.iptv`. Launch through `com.inakara.aypitivi/com.example.iptv.SplashActivity`, because MainActivity is not exported. OPS-006 moves away from the old installation identity to resolve its incorrect Fire TV tile; see the deployment guide for the one-time private settings/history migration and current device evidence. Ordinary updates still use the same deployment command and preserve the new installation's saved data.

## Validation and known limitations

Run the quick local regression suite after each implementation:

```powershell
.\test-smoke.ps1
.\test-smoke.ps1 -Lint  # Also run Android lint
```

The command uses the private JDK/SDK paths from .env (or existing environment settings), runs fresh app tests, both sets of environment fixtures and reporting/project checks, then prints **X/Y passed** with failed/skipped/error counts. It does not install anything or contact your IPTV provider. Tests use synthetic data and a loopback fake server; first-time Gradle/dependency downloads still need network access. Compilation remains incremental while test execution is fresh. Use JDK 17 as documented above.

Every requirement in [REQUIREMENTS.md](docs/requirements/REQUIREMENTS.md) has a generated companion row listing testcase IDs, descriptions and latest results. [TEST_CASES.md](docs/requirements/TEST_CASES.md) provides the complete testcase view; [scripts/test-catalog.json](scripts/test-catalog.json) owns stable IDs, selectors, coverage gaps and requirement-definition/status fingerprints. **Passed tests do not imply that every acceptance criterion or physical-device behavior is verified.** Manual cases remain Not run in this local suite; partial and superseded requirements retain their distinctions.

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
- Saved credentials/history remain in unencrypted app-private preferences; playlist records use private no-backup files. Both are excluded from backup/transfer. Explicit HTTP exposes credentials in transit; prefer verified provider HTTPS when available. App-controlled errors omit URLs/stacks; Android/platform diagnostics may still be private.
- Empty/scrolled focus targets have regression checks; physical remote and keyboard acceptance remain pending. The Settings account panel scrolls to keep controls reachable on smaller screens.
- Release builds enable R8/resource shrinking and have empty defaults. The generated release APK is unsigned; signing/distribution and minified device acceptance remain separate.
- Generated build files, Gradle caches, IDE files and local settings are excluded from public history. Local debug APKs and caches can contain personal defaults; do not upload them.

## Development approach

The deployment baseline works. Development follows the catalog-first review process in [REQUIREMENTS.md](docs/requirements/REQUIREMENTS.md). Keep requirement IDs traceable in implementation summaries and commits/PRs when created. After scope approval, make small, independently checked changes, keep the deployment guide current, and verify relevant remote navigation and playback on the Firestick. Record what was actually checked and what remains unresolved.

See [AGENTS.md](AGENTS.md) for repository-specific guidance for coding assistants.

## References

- [Android Gradle plugin 8.2 compatibility](https://developer.android.com/build/releases/agp-8-2-0-release-notes)
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
- [Amazon: connect to Fire TV through ADB](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html)
