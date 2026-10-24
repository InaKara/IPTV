# Deploy Aypi Tivi to a Firestick

**Agent deployment rule:** consult the user and obtain explicit approval before each installation/deployment. Approval to implement a requirement does not authorize installing it. Use `test-smoke.ps1 -Lint` and `deploy-firestick.ps1 -BuildOnly` for local validation while approval is absent.

**Ask before deploying or starting the app.** Before deployment, ask for approval and explain that the script installs, force-stops and starts Aypi Tivi. One explicit approval of that combined operation covers that invocation's stop/start. For any other app start, including tests or investigation, obtain approval first. The user may be using the Firestick.

After deployment approval, run only `deploy-firestick.ps1` and report its result. Do not add unrequested flags or extra ADB/device commands, tests, preference changes or remote/UI actions. The user withdrew DP-1's removal of force-stop/start before implementation; the existing script remains unchanged. This documentation update does not authorize a deployment or app start.

## Corrective deployment and latest user smoke — 24 October 2026

After the separate approval Deploy, the unchanged deployment script built successfully, installed the VOD-entry ANR correction with saved data retained and reported a successful app start (exit 0). No extra device commands or UI actions followed that deployment. The user subsequently reports no identified Live TV issues and successfully reaches Movies after an acceptable wait; the missing loading feedback is a new UX issue. This is positive sampled navigation/playback evidence, not a timed startup benchmark or complete stress/remote/Info acceptance.

The latest requested loading/icons/direct-actions/season-favorites/logging revision is [MD-3](../design/LIBRARY_ACTIONS_REVIEW.md), pending explicit requirement review/implementation approval. No new device session, deployment, app start or setting change is authorized by that request. The local-only/no-deployment statements below describe their earlier verification points.

## VOD-entry ANR correction — 24 October 2026; local verification before corrective deployment

The user separately requested Deploy after MD-2. The unchanged deployment script built, installed with data retained and reported a successful app start (exit 0), with no extra investigation commands. The user then reported two freezes followed by closure on opening VOD. Separately approved read-only crash/process-event diagnostics confirm two input-dispatch ANRs and process termination; the Java OOM crash-buffer records are older, from 22 October. No investigation start, stop, install, navigation or settings/data change was performed. Raw logs remain ignored/private.

The local correction removes metadata work from the five-tile VOD root and eliminates eager whole-catalog route construction under the shared account monitor. Matching is on demand with bounded retained results; only relevant favorites/history or selected/displayed aliases are persisted. Existing account/identity/progress data remains. Fresh smoke/lint passed 177/177 (137 app plus 40 shared), zero failures/skips/errors, in 39.0 seconds; debug assembly passed separately. See [investigation and evidence](../investigations/VOD_ENTRY_ANR_2026_10_24.md).

At that local-verification point the corrective APK had not been installed; the subsequent deployment and sampled user navigation are recorded above. Broad physical reopening/responsiveness/stress acceptance remains separate, and historical memory safety is not claimed resolved. Another deployment requires explicit approval of the script's install/force-stop/start operation. Older local-pass records below do not establish complete physical acceptance.

## MD-2 local verification — 24 October 2026; before deployment

The user explicitly approved all five reviewed follow-ups. Startup metadata reconciliation is deferred until a relevant destination after first layout; the VOD root uses equal tiles with root-only Playlist categories; Resume displays HH:MM; watched menus use current saved state; redundant Back/Refresh are removed with failure-only specific retry; live Info expires with controls and cleans up on forced hide/release. Existing playback, account data and refresh thresholds are retained.

Fresh `test-smoke.ps1 -Lint` passed 176/176 (136 app plus 40 shared), no failures/skips/errors, in 63.9 seconds. Debug lint: zero errors/32 warnings. Debug assembly passed separately; the local debug APK includes MD-2. See [MD-2 implementation/evidence](../design/METADATA_FOLLOWUP_REVIEW.md). The tests use synthetic local fixtures only.

At that local-check point MD-2 had not yet been deployed; its subsequent installation and reported VOD-entry ANRs are recorded above. Startup improvement is not yet device-measured, and new physical layout/remote/Info/watching/provider acceptance remains separate. The following deployment/user report concerns MD-1. Another deployment still requires explicit permission for the script's install/force-stop/start operation.

## MD-1 deployment and user report — 24 October 2026

The user explicitly requested “execute deploy-firestick.ps1. do nothing else”. That script alone built successfully, connected, installed the update with data retained and reported a successful launch (exit 0). No extra device commands, measurements or UI actions followed. This establishes installation/start, not full feature acceptance.

The subsequent user report confirms normal video and sound after pausing TRT 1 HD for about 60 seconds and resuming, with no reported jumping; a sampled movie's Resume works. It also reports considerably slower startup (untimed), live Info remaining after controls hide, unequal VOD root buttons, absent resume timestamps, unclear Retry, redundant Back/Refresh, duplicate watched/reset actions on one route and repeated Playlist categories controls. These issues are preserved as partial physical acceptance and the pending [MD-2 revision](../design/METADATA_FOLLOWUP_REVIEW.md), not silently marked fixed. Source inspection identifies optional pre-layout metadata/alias bookkeeping as a plausible startup contributor, not a proven measured cause.

No further Firestick session, deployment or app start is authorized by that feedback. Comparable startup timing and physical acceptance of any future correction require separate permission. The records below describe earlier local checks or earlier deployed builds as indicated.

## MD-1 local verification — 24 October 2026; before deployment

The user explicitly selected all three reviewed metadata stages. Provider library/details/seasons/episodes, local watched/resume/Continue watching, metadata sorts/Unwatched and compact native live Info are implemented in the checkout. Fresh local `test-smoke.ps1 -Lint` passed 171/171 checks (131 app plus 40 environment/project/runner), no failures/skips/errors, in 52.7 seconds after the final credential-change navigation check; debug lint has zero errors and 31 warnings. Debug assembly passed separately. No build/deploy script, dependency or toolchain migration was needed. Tests use synthetic local data/HTTP/player fixtures only.

At this local-verification point the MD-1 debug APK had not yet been installed or started on the Firestick; the later deployment/user report is recorded above. Full provider/physical library, VOD completion/episode/account, adult-filter/return/Info/cache and memory-pressure acceptance remain separate. Existing older MP2/live-control/device evidence below refers to the earlier deployed build. See [approved MD-1 design and implementation evidence](../design/METADATA_LIBRARY_REVIEW.md). Future deployment still needs explicit approval of the script's install/force-stop/start operation.

## Device validation — 24 October 2026; user already deployed

The user explicitly approved deployment and the described device checks, then confirmed that they had already installed the update: TRT 1 HD has sound, controls function and video remains visible behind them. The installed APK's SHA-256 matches the current local debug build. No agent installation was performed. This is basic user-reported playback acceptance, distinct from the measurements below and the older local-only records.

Two approved fresh-process cached starts and one actual same-account provider refresh completed without new recorded fatal/OOM errors. The lists contained 101,095 entries before refresh and 101,098 on the subsequent cached start. Highest sampled Java allocations were **81.32/85.64 MiB for starts** and **146.29 MiB during refresh**, against a **192 MiB** normal Java heap limit; final idle allocation was **52.33 MiB**. Refresh total PSS reached 266.19 MiB, which includes native/graphics/mapped memory and is not the Java heap budget. Memory queries can miss short peaks and perturb timings; this is not proof that the historical crash is fixed.

Device diagnostics in the initial user process show supported/selected MP2 stereo 48 kHz and decoder initialization, supporting the user's audible TRT confirmation. Direct screenshots also verify accepted 10-second steps, remote Live return from about 22 seconds behind, a visible paused frame across a 91.3-second pause, resumed moving video through about 79 seconds, and settings/Back/player release. The specific behind-live-window error was not recorded, so its recovery branch and provider fallback remain unprovoked. Precise renderer identity, isolated MP2 memory and audible post-pause comparison/synchronization remain unverified. See [FIRESTICK_VALIDATION_2026_10_24.md](../investigations/FIRESTICK_VALIDATION_2026_10_24.md) for samples, timing limits and acceptance boundaries.

No app data was cleared or channel list truncated, and account/power/filter/density settings were not changed. An initial screensaver was dismissed through normal remote input before profiling. A stale accessibility snapshot initially suggested browsing during playback; actual native hierarchy and direct screenshots disproved that exit after the user confirmed no remote use. These playback checks use direct screenshots. At completion, Back returned to browsing, removal of the native player was verified, both temporary dump files were removed and the connection closed. No app/build/deployment behavior changed; current-session approval does not remove approval requirements for future deployments/starts.

## Startup/connection investigation — 23 October 2026

The latest user-approved deployment built, installed and reported a successful app start. The subsequent user report establishes that this was not successful browsing acceptance. The user explicitly granted repeated stop/start/deploy permission for the current investigation session; normal future deployment/start consultation rules remain unchanged.

[STARTUP_FAILURE_INVESTIGATION.md](../investigations/STARTUP_FAILURE_INVESTIGATION.md) records a historical 22 October, 192 MiB out-of-memory parsing crash, its preserved next-launch notice, and the current cache rejection of 95 blank category values in an otherwise valid 100,961-channel list. Saved account values still match private configuration. One unchanged-app restart reproduced download-then-failure timing without a new recorded fatal crash. No implementation/deployment/settings change was made during diagnosis; the account was not cleared. The user then explicitly approved SF-1; its implementation and subsequent device acceptance are recorded below. Historical large-list memory safety remains unresolved.
## SF-1 deployed and browsing verified — 23 October 2026

After explicit SF-1 implementation approval, the local smoke/lint suite passed 121/121 checks and the optimized release build passed separately. The unchanged deploy-firestick.ps1 built, installed with -r and launched the fix under the user's session-wide stop/start/deploy approval. No uninstall, global data clear or account/settings replacement was used.

The real provider list of 100,967 records saved successfully and reached visible Categories. An idle display initially deferred UI work; waking it allowed browsing to finish. That first trace includes the idle interval and is not an app processing benchmark. A subsequent fresh process reached visible Categories from cache, with no provider/download phases and no new fatal crash. Its one main-startup trace was 14.857 seconds after the separate two-second splash (cache 5.010 s; history 0.247 s; grouping/indexing 7.903 s). This is a single sample, not an average or instant-loading claim.

Saved account fields remained identical and pre-existing recent-history references were preserved. Private screenshots, UI dumps, backups and traces remain ignored. Temporary UI dump files and the debugger bridge were removed. The app was left at Categories. See docs/investigations/STARTUP_FAILURE_INVESTIGATION.md for the exact scope and limits. Physical playback/screensaver and general memory/refresh stress acceptance remain unverified; the earlier parsing OOM is not claimed fixed. Future unrelated deployments/starts still require approval.
## SC-1 local implementation and paused device validation — 22 October 2026

Automatic saved-list startup, 24-hour background refresh, account/manual refresh controls in Settings and playback-scoped keep-screen-on are implemented. Final local smoke/lint passed 120/120 checks (80 app plus 40 environment/project/runner checks), with no failures/skips/errors, in 39.6 seconds. Debug and optimized release assembly plus release lint passed separately. Both lint variants reported zero errors and 26 warnings. All four release defaults remain empty; R8 output retains the snapshot schema-version field through targeted rules. The requirement-linked outcomes are recorded in docs/requirements/TEST_CASES.md.

A debug update was installed using `install -r` and launched with synthetic localhost fixture settings before the user required consultation before every deployment. Original preferences were backed up and verified first; there was no pre-existing playlist cache. This installation is not evidence of usable playback, cached-start performance or screensaver prevention. Further device validation is paused. The user then explicitly approved restoring the original settings. The verified original preference backup was restored byte-for-byte and the app was left closed, with no additional install/uninstall or global data clear. The temporary localhost server was stopped. The installed test build predates final local refinements; the final local APK has not been deployed.

Read-only idle settings showed a five-minute screensaver timeout and twenty-minute sleep timeout. Neither was changed. The planned local-video inactivity test and large saved-list startup measurements have **not** been completed; no performance or physical acceptance claim is made.

After a separately approved deployment, expected behavior is: startup loads an existing current-account snapshot automatically; a fresh snapshot makes no playlist request; one over 24 hours old appears first and refreshes once in the background. Without a usable snapshot, complete saved/default account settings trigger a download; missing settings or initial failure expose Settings setup/retry. Settings contains density, masked account fields, Save and load, Refresh channel list and last-success/error status. Failed refreshes retain working data. Pause, Back and backgrounding must restore normal idle behavior.

## One command for normal development

Create your private `.env` once (see below), then open PowerShell in this project and run:

```powershell
.\deploy-firestick.ps1
```

The script reads local settings, checks JDK 17, builds a fresh debug APK, connects to the Firestick, installs with `-r` to preserve saved app data, and launches Aypi Tivi. It stops on build, connection or installation failure. Confirm picture, sound and the changed controls on the television after deployment.

The installed application ID is **com.inakara.aypitivi**. Its activity class still belongs to the Kotlin namespace, so its full launcher component is `com.inakara.aypitivi/com.example.iptv.SplashActivity`. The script handles this pair. The one-time move from the former com.example.iptv installation is complete on the verified Firestick; future updates need only the normal command above. Changing an application ID does not automatically transfer another installation's settings/history; the approved private migration and its verification are recorded below.

Useful options:

```powershell
.\deploy-firestick.ps1 -Check        # Build, unit tests, lint, install and launch
.\deploy-firestick.ps1 -BuildOnly    # Build without touching the Firestick
.\deploy-firestick.ps1 -InstallOnly  # Install/launch the existing APK without rebuilding
```

`-Check` can be combined with `-BuildOnly`. Use `-InstallOnly` only when the APK already contains your latest changes. Installing an APK does not compile source code.

`-Check` now calls test-smoke.ps1 -Lint first, refreshing the testcase rows in docs/requirements/REQUIREMENTS.md and docs/requirements/TEST_CASES.md, then builds the APK. A failed suite prevents deployment. For the fast local suite alone, use .\test-smoke.ps1; it does not connect to the Firestick. Its summary separates tests from lint/build checks and keeps manual/partial requirement coverage explicit.

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

Use canonical uppercase keys in literal `KEY=value` lines. Both readers reject lowercase/case-variant keys, malformed entries and duplicates. `-Check` runs the same synthetic environment fixtures through both readers. Blank lines and lines beginning with `#` are ignored. Matching single/double quotes around a value are removed. No variable expansion, shell execution, escape processing or inline comments are performed. Use forward slashes in Windows paths. Values containing `=` or `#` are supported.

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
- **Playback failure:** inspect stream format and player error. The review candidate includes HLS/DASH modules and per-channel user-agent support. RTMP is not supported. Try an HTTP(S) stream with a supported device codec.

To inspect recent errors with your own SDK/device values:

```powershell
adb -s '<firestick-ip>:5555' logcat -b crash -d -t 200
adb -s '<firestick-ip>:5555' logcat -d -t 500 'AndroidRuntime:E' 'AypiTivi:W' '*:S'
```

Use the full path to adb.exe if it is not on PATH. The review candidate saves a generic crash notice for the next launch and delegates to Android's normal crash handler. It does not display raw crash stacks. App-owned messages omit provider URLs; Android/platform diagnostics may still contain private information. Logs and crash messages can contain provider details or authenticated URLs; redact before sharing and keep logs out of Git.

## Verification record

On 21 October 2026, the manual debug build and installation succeeded and the user reported that the app works. The device runs Android 9 / API 28, meeting the app minimum of API 26. That recovery included the login keyboard handler and provider-default update. Individual VOD/keyboard/relaunch checks were not separately reported.

The subsequent .env/deployment-script change was also verified:

- PowerShell syntax, literal .env parsing and native-command failure handling passed.
- The script's -BuildOnly -Check run passed the debug build, parser unit test and Android lint (0 errors / 19 existing warnings).
- Generated debug defaults matched the private .env, including removal of the URL's final slash.
- Release defaults were verified empty with .env present, and a debug build without .env passed with empty defaults.
- A normal script run rebuilt with local defaults, installed successfully on the Firestick and received a successful activity launch. Playback after this particular update has not been separately reported.
- The public source tree was checked for the known private values; .env, generated outputs and local tool settings are excluded. Publication uses a fresh initial commit rather than the old history containing private defaults.

That record describes the earlier deployment baseline. The subsequent code-review candidate updates the Compose BOM to 2024.02.02 and independently aligns lint to 8.3.2 while keeping AGP 8.2.2, Gradle 8.2, Kotlin 1.9.22 and JDK 17. See docs/reviews/CODE_REVIEW_FIXES.md for current local checks and pending Firestick acceptance.

## Local remediation checks — 22 October 2026

The review candidate passed debug and optimized unsigned-release builds, 29 debug tests (including simulated remote focus), 26 release-variant tests, eight shared fixtures in each environment reader, and debug/release lint (0 errors / 21 warnings, with the formerly skipped Compose registry running). Release defaults are empty and the public candidate/release artifact passed the configured private-value scan.

No Firestick installation or real-provider request was performed for this candidate. Review docs/reviews/CODE_REVIEW_FIXES.md, then use the normal deployment command and its acceptance checklist. Release unit tests run before optimization; minified device runtime is still unverified. The earlier installed app remains the last physical-device baseline.

## Logo and startup follow-up — 22 October 2026

The first logo/timing candidate, version 1.1.1 (3), was installed with saved app data preserved. Local PNG/SVG logos and the same gray fallback for missing/404/unsupported images were visually verified on a five-channel fixture. That host suite passed 88/88 checks plus debug lint; its release build and release lint also passed. Real external channel-logo probes were not performed, following the user's local-fixtures-only instruction.

Three synthetic 100,915-entry / 24.21 MiB device runs reached the first browsing layout in 17.65–19.82 seconds. Streaming download/parsing accounted for 15.07–17.59 seconds; history preparation 0.35–0.41 seconds and grouping 0.17–0.27 seconds. These are loopback-fixture measurements, not real-provider comparisons. See docs/investigations/UX_INVESTIGATION.md.

Debug startup records use INFO because this Firestick drops DEBUG records by default. To inspect only the fixed numeric timing records, with your own configured ADB/device selection:

```powershell
adb -s '<firestick-ip>:5555' logcat -s 'AypiStartup:I' '*:S'
```

The trace ends at the first browsing list layout and excludes channel playback/image readiness. It contains no provider/account/channel names, URLs or raw exception messages. Release disables it.

The initial launcher APK resources and native rendered artwork were corrected, but the original-ID Fire TV tile still showed the blue/white rectangle. Replacement installation and a reboot did not resolve it; launcher force-stop was refused by the OS. The user then explicitly approved uninstall/reinstall with backup restoration. A fresh verified backup was taken, uninstall/install succeeded, preferences were restored byte-for-byte, and launch succeeded. A further restart still left the app-list tile unchanged; selecting that tile was confirmed to open the app. UI-004 was Partial at that stage. Launcher caching/metadata was suspected without inspecting its source. Repeating an uninstall or resetting launcher data was unnecessary; the subsequent identity migration resolved the issue.

A temporary test APK with the same artwork, a different installation ID and empty login defaults displayed the correct classic icon beside the original blue/white tile. The test copy was removed and normal build outputs restored. This tied the difference to installation identity without exposing the launcher's metadata source. The user then approved OPS-006; the completed migration below resolved the tile issue.

All local fixture settings were removed and original saved settings/history were restored and verified byte-for-byte, including after the approved reinstall and restart. The bridge was removed. The remaining launcher defect at that stage was resolved by the identity migration below. Future direct-to-list startup and cache/refresh policy remain LOGIN-008, Open for later review.

## Completed identity migration — 22 October 2026

**Version 1.1.2 (4), com.inakara.aypitivi, is installed.** The final app-selection screen shows the classic logo; the old blue/white tile is gone. UI-004 and OPS-006 are Implemented.

The one-time approved migration used this order:

1. Verify the new APK identity and that its signing certificate matches the original; stop the old app and take a fresh private backup of its complete shared-preferences file (`aypi_tivi_prefs.xml`).
2. Install the new identity while retaining the old installation. Stop the new process, copy the preferences without text re-encoding, and compare the restored file's SHA-256 with the original.
3. Launch the migrated app and visually verify the classic app-selection tile. Selecting it was confirmed to open the new package's MainActivity.
4. Run the ordinary `.\deploy-firestick.ps1` workflow to build/install/launch a real version upgrade from 1.1.1 (3) to 1.1.2 (4). Verify the installed version, unchanged preference hash and correct tile afterward.
5. Only after these checks, uninstall the obsolete com.example.iptv installation. Verify its absence, the remaining classic tile, its launch and unchanged migrated preferences.

Saved settings/history matched the original byte-for-byte after migration, the normal update and removal of the old app. The app was left at its normal login screen. Private backups, APKs, screenshots and raw logs remain under ignored build output. No real-provider playback or external channel-logo requests were needed for this migration. This is a completed device record; routine deployment never repeats the legacy restore or uninstalls either identity.

The final 1.1.2 candidate passed **91/91** fresh automatic checks in 27.2 seconds plus debug lint, release build and release lint. Release account defaults were verified empty. Three new deployment fixtures cover the new-ID/unchanged-activity pair and install/launch failure handling; the compiled TV intent case checks actual launcher resolution. The new fixtures also passed under Windows PowerShell 5.1 after using a structured JSON argument record. The installed device build is debug; optimized release runtime and provider playback remain separate acceptance.

## BR-1 browser deployment — 22 October 2026; feature acceptance pending

The user separately requested “Deploy this version” on 22 October 2026. The normal `.\deploy-firestick.ps1 -Check` workflow completed: fresh tests/lint passed, the current source built, `install -r` returned Success, and the launcher start reported success. The installed APK hash matches the fresh local debug APK, version 1.1.2 (4), and the saved preferences/history file matches its pre-update hash exactly. No uninstall, app-data clearing or legacy migration was performed. An additional foreground-activity sample did not confirm MainActivity; no interactive browsing/playback acceptance is claimed from the launch command. Private deployment details remain under ignored build reports.

Local debug assembly and the fresh smoke suite with lint passed on 22 October 2026; current individual results are generated in docs/requirements/REQUIREMENTS.md and docs/requirements/TEST_CASES.md. Native rendering verifies the three density layouts and amber tile/sidebar pixels using synthetic local channels; the first non-remote launch also initializes keyboard/remote focus. Earlier focus/dialog scheduling failures and the channel-count plural warning were corrected and rerun. The remaining lint warnings concern the existing toolchain/resources; no dependency migration or check suppression was introduced. Installation evidence is recorded above; actual browser, keyboard, remote and playback acceptance remains pending.

After authorized installation, verify Categories and Channels with all three densities, including 20 fully visible Standard tiles. Check amber focus on every sidebar icon and both tile variants; focus movement must not activate a destination or playback. Exercise empty recents/Favorites, partial final rows, long/scrolled category and channel lists, and sidebar return to the saved grid item. Back from a category restores Categories before the root exit dialog.

Use Menu to add/remove a channel, restart to verify favorites/density persistence, and confirm Favorites Back restores its previous browser view. In Search, focus the field without opening the keyboard, use OK to edit, dismiss the keyboard before leaving Search, and verify results and focus survive playback return. Closing Settings must restore the prior view. Play a compatible existing stream, switch channels with the unchanged controls, then return: playback/audio must stop and the correct originating channel must regain focus. These are pending physical-device checks, not claims based on a local build or simulated playback destination.

## UX-1 local validation and read-only audio inspection — 23 October 2026

UI-010/011 and PLAY-009 implement branded startup status, larger fitted tile artwork and native player focus/key routing. Fresh smoke with debug lint passed 128/128 in 34.3 seconds; debug and optimized unsigned-release builds plus release lint passed. Debug/release lint have zero errors (26/33 warnings, including seven release-only private-override/library-resource warnings for the pinned native settings rows). The release shrinker report confirms the two layouts and focus/tint resources remain reachable; future Media3 upgrades must revalidate these overrides. Native settings-row resource declarations were corrected and covered by inflation/focus/contrast checks. The current local APK has not been installed; physical startup, complete remote/popup behavior and readable larger logos remain acceptance checks after separately approved deployment.

PLAY-008 remains Partial. A bounded PC metadata probe found H.264 video plus MP2 stereo audio at 48 kHz on the reported channel. The user explicitly approved read-only codec inspection; six system/vendor codec files declare an MP3 decoder for audio/mpeg but no MP2 declaration. The debugging transport was connected solely to read these files. No install, app stop/start, navigation, input, power or setting changes were performed. This supports a codec-compatibility explanation but does not prove runtime decoder selection or audible output. AU-1 / PLAY-010 proposes a software decoder and awaits separate approval. See docs/design/LATEST_APP_REVIEW.md; private probe/device data stays ignored.

## LV-1 local validation — 23 October 2026; not deployed

UI-012/BROWSE-013 implement the ordered Live TV, VOD, Favorites and Search sidebar with bottom Settings and initial screen-icon focus. Existing per-entry classification partitions provider categories while preserving canonical identities, shared Favorites/Search/history and independent section/category bookmarks and returns. Fresh `.\test-smoke.ps1 -Lint` passed 133/133 in 35.32 seconds with zero failures/skips/errors. Debug assembly, optimized unsigned-release assembly and release lint passed; debug/release lint reported zero errors and 30/34 warnings. The release build was an extra local packaging check; the personal Firestick workflow continues to use the debug APK and relevant local checks.

No device connection, install, app stop/start or provider request was performed for LV-1. After separately approved deployment, physical acceptance must cover five-control remote focus/readability, both section roots and mixed categories, empty sections, offscreen playback return, global Favorites/Search and matching Back targets. Local Compose/controller tests do not establish device playback or full Activity recreation acceptance. No audio decoder or provider API change was added. See [LIVE_VOD_BROWSER_REVIEW.md](../design/LIVE_VOD_BROWSER_REVIEW.md).

## CF-1 local validation — 23 October 2026; not deployed

UI-013/SET-008 implement current-view/category headings and the device-wide saved adult-category filter, active by default. Matching uses a case-insensitive adult substring in provider category names and covers sections, recents, Favorites, Search and browser playback collections while retaining saved data and IDs. Hidden-category returns recover the matching section root. Fresh `.\test-smoke.ps1 -Lint` passed **139/139** in **32.01 seconds**, with zero failures/skips/errors. Debug lint reported zero errors and 30 warnings; debug APK assembly passed. Native local rendering verifies single-line long-title ellipsis and switch amber focus; the Settings fixture preview was visually inspected. No release build was needed.

No install, app stop/start, provider request or other device action was performed for CF-1. After separately authorized deployment, check actual TV heading readability, switch navigation/status persistence across restart/account changes, all browsing routes and real playback stepping while filtered, plus returns when the previous category becomes hidden. Full Activity lifecycle and device playback remain pending. See [CATEGORY_FILTER_REVIEW.md](../design/CATEGORY_FILTER_REVIEW.md).

## Read-only open-issue investigation — 23 October 2026

The user requested investigation and allowed Firestick use if needed. The device was reachable with wakefulness Asleep and the existing app process already running. Read-only heap/ABI/power/memory and current-process fixed diagnostic-tag queries were performed without waking the display, starting/stopping the app, installing, navigating or changing settings. Reported runtime: API 28, armeabi-v7a/armeabi and a 192 MiB normal heap growth limit (384 MiB maximum heap configuration). No existing fixed AypiAudio records were available to establish runtime MP2 support/selection.

PC provider/API/XMLTV/HLS inspection and standalone current-code desktop allocation/decoder diagnostics are recorded in [OPEN_ISSUES_INVESTIGATION.md](../investigations/OPEN_ISSUES_INVESTIGATION.md). They establish useful provider capabilities and investigation leads, not device playback, audible audio, native 4K output, fresh Android startup/refresh peaks or screensaver acceptance. No new app behavior, native decoder build or deployment was included. Future app starts/deployments still need their described approval; visual/audio/remote checks need a viewing session.

## References

- [Amazon: connect to Fire TV through ADB](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html)
- [Android Gradle plugin 8.2 compatibility](https://developer.android.com/build/releases/agp-8-2-0-release-notes)
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html)

## AU-1 MP2 local validation — 23 October 2026; not deployed

The explicitly approved PLAY-010 integration adds official Media3 1.2.1 FFmpeg audio after platform audio, with MPEG-only FFmpeg 6.0.1 libraries built from pinned sources using workspace-local Linux NDK r26b. See [FFMPEG_BUILD.md](FFMPEG_BUILD.md) for first-build preparation, clean/rebuild, license/source/relink material and local native checks. Ordinary Gradle now fails clearly if those artifacts are missing/stale. No Gradle/AGP/Media3 version or deployment script behavior changed.

Both ARM libraries pass ELF/JNI/dependency/configuration checks and reproduce byte-for-byte on a repeated native build. A static Android Bionic x86_64 executable running under WSL decoded synthetic MP2 to non-silent 48 kHz stereo PCM (84 frames; RMS 2053.5), with AAC absent from that narrow decoder build. This is not an ARM/JVM/audible device test. Fresh smoke/lint passed **140/140 in 38.57 seconds**, zero failures/skips/errors; lint 0 errors/31 warnings. Debug APK assembly and byte-identical ARM/license packaging checks passed; APK 15,643,148 bytes, about 337 KiB above the preceding local debug build. AGP could not apply its own native strip step because its SDK NDK is unconfigured; the explicit source build already strips these binaries and packaging equality is verified. Earlier header/annotation/lint integration failures are resolved; no unresolved build failure remains.

No Firestick commands, install, app stop/start, display wake or provider request occurred for this implementation. After separately approved script deployment, actual Android native loading, selected decoder, audible TRT 1/known-good audio and synchronization/memory remain physical acceptance. Do not mark them passed from these host results. EPG is Deferred; live controls LP-1 and metadata-memory reduction MR-1 await selected scope approval.

## LP-1 live controls — 23 October 2026; not deployed

PLAY-012 was explicitly approved with Approve LP-1 implementation. Local implementation adds actual-window 10-second native/dedicated live steps, separate amber-focus Live with lag display, exact-provider/account HLS validation and one-time original fallback, and bounded expired-pause recovery with feedback and retained play intent. VOD elapsed/total times and hidden 30-second D-pad shortcuts, Menu/Back/timeout and resource release are retained. See [LIVE_PLAYBACK_REVIEW.md](../design/LIVE_PLAYBACK_REVIEW.md).

Fresh smoke/debug lint passes **146/146 in about 34 seconds**, zero failures/skips/errors; lint 0 errors/31 warnings. Six new meaningful cases include actual Media3 HLS rolling-window preparation/rejection, route/identity boundaries, command bounds/recovery, native focus/time/pixel/key/timeout/cleanup checks. Debug APK assembly and native ABI/license packaging verification passed. Native live/VOD previews were inspected after a completed drawing frame. Earlier test failures exposed a reused-data-source fixture issue and an actual unavailable-Live focus edge case; both were corrected and the final run is fresh. No unresolved build/test failure remains.

No real provider request, Firestick command/install/app start/stop/display wake or setting change occurred for LP-1. Deployment must still be separately approved as the described deploy-firestick.ps1 install/force-stop/start operation. Then actual provider live-window seeking, safe Live timing, prolonged pause expiry, original-source fallback, audible MP2/known-good audio, synchronization and physical remote/TV readability need acceptance; host fixtures do not mark these complete. No memory optimization, EPG feature, release build or publication is included.

## MR-1 metadata memory — 23 October 2026; not deployed

The user explicitly approved MR-1 / REL-004. Bounded per-load category/user-agent sharing and per-entry equal display/TVG-name sharing now run in both the InputStream parser and streamed snapshot reader. Values, canonical occurrence IDs, schema, account boundaries and existing refresh/playback retention are preserved. See [MEMORY_REVIEW.md](../design/MEMORY_REVIEW.md) for implementation limits and the allocation tradeoff.

Fresh local smoke/debug lint passed **150/150**, with zero failures/skips/errors; lint reports zero errors and 31 warnings. Debug APK assembly passed. The same offline 100,961-entry initial/cache/refresh desktop workload retains about 18 MiB less after loading and 37 MiB less with old/new catalogues retained. Complete metadata/identity comparisons and snapshot byte equality passed. These are host results, not Android peak-memory or crash-resolution evidence; cached loading creates about 5.35 MiB more short-lived allocations. No unresolved build/test failure remains.

No Firestick command, deployment, app stop/start, provider request or setting change occurred. A separately approved deployment must use the retained install/force-stop/start script. Android cold/cached start and refresh peaks, known-good/MP2 playback memory and audible/remote/live acceptance still need their own described device session; implementation approval does not authorize those extra actions.

## PV-1 playback visibility — 24 October 2026; not deployed

PLAY-013 was explicitly approved with approved, implement. Native controls now use a compact translucent bottom panel, with the upper three quarters of the picture clear at normal brightness on supported TV layouts. Existing focus/actions/seek/Live/timeout behavior is preserved, and short messages clear the visible panel without restarting their timer. See [PLAYBACK_VISIBILITY_REVIEW.md](../design/PLAYBACK_VISIBILITY_REVIEW.md).

Fresh smoke/debug lint passed **153/153 in 44.6 seconds**, zero failures/skips/errors; lint **0 errors/31 warnings**. Debug APK assembly passed. Native bright/dark pixel and widget-bound checks pass at 960 x 540 mdpi and 1920 x 1080 xhdpi, with inspected live/VOD previews. Surface/state and repeated live/VOD playing/paused seeks, actual settings popup dismissal, Back/timeout and Compose notice position/event-timeout checks pass. Earlier row-padding and paused-fixture layout failures are resolved; no unresolved local build/test failure remains.

No Firestick command, provider request, installation, app start/stop or setting change occurred. After separately approved script deployment, confirm real decoded video and paused frames remain visible while navigating controls and seeking, including Live and settings dismissal. The local frame fixture is not moving-video acceptance. Deployment approval must still describe the script's install, force-stop and start; additional viewing/profiling/device actions need their own scope.
