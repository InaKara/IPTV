# Deploy Aypi Tivi to a Firestick

## FS-1 local Favorites startup — 26 October 2026; not deployed

The user selected Approve Favorites implementation first after the reviewed BROWSE-019 proposal and clarification that the current installed/build default was still Live TV. This approves the local Favorites change and selected startup-only UI-012/BROWSE-013/BROWSE-011 exceptions, not installation. Fresh sessions and successful account resets now open Favorites with the heart selected/focused. Initial Favorites without a predecessor uses the existing Cancel/Exit dialog; ordinary Favorites visits retain their prior section and normal grid/menu behavior. Sidebar order, existing retained/reused/foreground sessions, playback and auxiliary returns remain.

TC-APP-168–172 add canonical/filtered/unavailable/late-reference controller cases, actual first-layout/remote populated/empty Favorites, playback and Search/Settings return assertions, and real account preferences/SQLite reconciliation gated after first layout with zero provider attempts. Actual dialog TC-APP-164 verifies initial Favorites Cancel/Exit/task/data safety. Existing Live-context tests explicitly select Live; startup timing tests identify Favorites. The first focused run found startup heart focus leaking into an ordinary Favorites visit; its bookmark handling was corrected, and fixtures now use the approved account-reset default, actual empty text and remembered sidebar returns. All 28 focused checks then passed; the final full run includes the additional zero-provider-attempt assertion.

Fresh test-smoke.ps1 -Lint passed 212/212 (172 app plus 40 shared), zero failures/skips/errors, in 41.3 seconds. Lint has zero errors/33 warnings. Debug assembly passed separately. Final fresh catalog maintenance after correcting the manual default-focus title also passed 212/212, zero failures/skips/errors, in 37.3 seconds. Requirement definitions, mappings and generated companion rows are current; no unresolved local build/test failure remains.

The APK is local and not installed. No Firestick command/start/control, provider access, deployment, commit or push occurred during implementation. Full physical startup/remote/Home and retained/reused Activity/process/window acceptance remain separate. A further deployment needs explicit approval of deploy-firestick.ps1 installing the update, force-stopping Aypi Tivi and starting it; afterward run only that script and report its result.

## ST-1 local title correction — 26 October 2026; not deployed

The user explicitly approved UI-017/ST-1 with approved, implement after the title continuity revision/in-chat overview. Native launch and later splash/loading/error now draw the same existing splash_background artwork. Normal TV preserves its initial 320 dp illustration and 200 by 40 dp title geometry. Shared compact dimensions reserve space for larger status text and the focused retry action. The accessible title is fixed artwork; status/error text remains scalable. No startup delay/timing, account/cache behavior, launcher, browser theme, EX-1 routing or dependency/deployment-script change was made.

TC-APP-165–167 compare actual themed native launch and rendered startup/error title ink/shape/bounds and complete branding pixels at normal/high TV density and compact size; larger-text cases verify readable separate status, fully visible focused retry and remote Select reaching Settings. Existing TC-APP-158/082 rendering assertions were strengthened and resource/timing/routing regressions retained. Normal, high-density and compact screenshots were visually inspected locally. The first compact check caught error-action overflow, corrected in shared compact placement.

Final fresh test-smoke.ps1 -Lint passed 207/207 (167 app plus 40 shared), zero failures/skips/errors, in 53.9 seconds. Lint: zero errors/33 warnings; debug assembly passed separately. An earlier full run had a TC-APP-136 library Retry timeout and eight unexecuted Gradle environment fixtures; the unchanged Retry test passed in isolation and then the complete fresh run passed. Its cause was not established; no library source/test adjustment was made for it. Mappings and generated companion rows record the final passing run.

No Firestick command/start/control, provider access or installation occurred during ST-1 implementation. The new APK is local, not installed. Actual Fire OS starting-window/inset/title continuity still needs separately approved deployment and physical observation. Preserve the per-deployment install/force-stop/start permission policy below.

## EX-1 deployment and startup-title feedback — 26 October 2026

The user explicitly approved the described deploy-firestick.ps1 install/force-stop/start operation after EX-1 local implementation. Only the unchanged script was run. It built successfully, installed with saved app data retained and reported successful launch, exit 0; no extra device actions followed that invocation. This establishes installation/start, not Exit/Home or other physical acceptance.

The next user report said Aypi Tivi changes size and position during startup. Source-only inspection found fixed 200 by 40 dp title artwork in the initial launch drawable, but themed Compose Text and line-height-based layout afterward. That explained a plausible handoff mismatch; no device recording established the exact frame. Existing local tests compared logo geometry/colors, not title continuity. UI-017 was marked Partial with [ST-1](../design/STARTUP_TITLE_STABILITY_REVIEW.md) pending corrective approval at that point. No startup correction, implementation-test change, provider access, device command/start/control or further deployment accompanied the proposal. Exit/Home acceptance remains unconfirmed. The subsequently approved local ST-1 correction is recorded above.

ST-1 proposal catalog/mapping maintenance passed fresh test-smoke.ps1, 204/204 existing checks with zero failures/skips/errors in 43.7 seconds. That was baseline/catalog verification before implementation. Installation output in project-contract fixtures was mocked, with no Firestick/provider access.

## UX-2 deployment and reported Exit failure — 26 October 2026

After the user explicitly approved the described install/force-stop/start operation, only the unchanged deploy-firestick.ps1 was run. It built successfully, installed with saved app data retained and reported successful launch (exit 0); no extra device actions followed that invocation. The user subsequently reports Exit reopening the app and showing the crash/restart screen. Thus Exit/device acceptance has failed despite successful installation/start.

Read-only existing crash/event logs and activity-stack snapshots were then inspected in response to this new report, without starting/stopping/navigating/installing the app or changing settings. At device-clock 09:37:36, an app-requested finish of one MainActivity record is followed by resume of a different MainActivity in the same task and fatal IllegalStateException, FocusRequester is not initialized, at HomeScreen.kt:229. Splash then consumes the genuine crash marker and opens ErrorActivity. Older OOM buffer entries are historical. Raw diagnostics stay ignored/private. The subsequently approved correction is recorded below; the UX-2 installed build still has the reported failure.

## EX-1 local correction — 26 October 2026; before deployment

The user explicitly approved BROWSE-008/REL-008 with approved, implement after the corrective revision and in-chat overview. Confirmed Exit selects and removes the complete own AppTask, including when the current activity is not the task root. Startup uses single-top/clear-top Main reuse and a resumed-only cancellable two-second Splash handoff. Browser/library/dialog focus is bound to placed targets, current commands/routes and a live resumed host; closing-task, stopped or detached targets cannot receive old requests. Saved data, Cancel, nested Back and genuine crash notice/delegation remain.

TC-APP-159–164 add framework task/ownership/data and controlled Activity handoff assertions, actual Splash marker handling, queued/stale/paused/removed focus, foreground timer and actual dialog/browser remote Cancel/Exit integration in a managed ComponentActivity. They use Robolectric AppTask shadows; they do not execute Fire OS task-manager removal or a complete Hilt MainActivity duplicate stack. Early local fixtures had Compose-clock contamination and an unreliable simulated touch; the managed Compose environment and remote Select resolved those check issues. The API-28 SQLite fixture closes explicitly. Temporary diagnostics were removed; no dependency, deployment-script or permission-policy change was needed. See [EX-1 implementation and limits](../design/EXIT_LIFECYCLE_REVIEW.md).

Final fresh test-smoke.ps1 -Lint passed 204/204 (164 app plus 40 shared), zero failures/skips/errors, in 59.4 seconds. Debug lint has zero errors and 33 warnings; debug assembly passed separately. Requirement mappings and generated test rows are current. Earlier fixture failures were corrected before this passing run; no unresolved local build/test failure remains.

Physical acceptance remains unrun. After separately approved deployment, check root Cancel, confirmed Exit reaching Home without reopening/notice, a later explicit launch and sidebar/grid/search/settings/library returns. Agent app starts or device input still need separate explicit approval. No Firestick command, provider access, app start or deployment occurred during this implementation. The local APK must not be described as installed.

## UX-2 local verification — 26 October 2026; not deployed

After explicit scope approval, LIB-007/BROWSE-017/REL-007/BROWSE-018/UI-017 implement truthful local preparation feedback, explicit-submit search, bounded inactive library payloads with retained disk/durable data, exact search-result browse jumps and larger cream/burgundy startup branding. Fresh `test-smoke.ps1 -Lint` passed 198/198 (158 app plus 40 shared), zero failures/skips/errors, in 40.6 seconds; lint zero errors/33 warnings and debug assembly passed separately. The additional warning is the classic startup wordmark vector path length; no check was suppressed. Canonical result focus, submit focus and a series Menu fixture were corrected before that passing run. Initial sandbox Gradle-cache access failures were resolved with authorized cache access; no unresolved local build/test failure remains. Final catalog-status maintenance is followed by a fresh smoke run recorded in generated rows.

Native startup rendering was visually inspected on the host. Actual Fire TV keyboard/remote/readability, memory pressure and provider-wide source behavior remain unaccepted. PLAY-016/017 bounded PC sampling is documented in [the audio/subtitle investigation](../investigations/AUDIO_SUBTITLES_2026_10_26.md); echo cause and selected-device audio/subtitle behavior remain unverified. The user need not listen for header inspection; future audible acceptance requires a separately approved listening session. No audio/subtitle policy changed, and no Firestick command/start/playback/install, deployment, commit or push occurred. Project-contract test installation messages were mocks. Obtain separate approval before any real installation or app start under the rules below.

Final UX-2 status/mapping maintenance passed fresh smoke/lint **198/198 in 40.4 seconds**, zero failures/skips/errors; lint zero errors/33 warnings. An intervening timing-dependent search-cancellation fixture was corrected to use deterministic Main scheduling; it had incorrectly assumed a very fast completed result must be canceled. No product behavior was changed for that test. Current generated rows record the passing run; no unresolved local failure remains. Debug assembly is refreshed after the final test-source change.

**Agent deployment rule:** consult the user and obtain explicit approval before each installation/deployment. Approval to implement a requirement does not authorize installing it. Use `test-smoke.ps1 -Lint` and `deploy-firestick.ps1 -BuildOnly` for local validation while approval is absent.

**Ask before deploying or starting the app.** Before deployment, ask for approval and explain that the script installs, force-stops and starts Aypi Tivi. One explicit approval of that combined operation covers that invocation's stop/start. For any other app start, including tests or investigation, obtain approval first. The user may be using the Firestick.

After deployment approval, run only `deploy-firestick.ps1` and report its result. Do not add unrequested flags or extra ADB/device commands, tests, preference changes or remote/UI actions. The user withdrew DP-1's removal of force-stop/start before implementation; the existing script remains unchanged. This documentation update does not authorize a deployment or app start.

## SI-1 Settings software information — 24 October 2026; not deployed

The user explicitly approved SET-010 after adding 24-hour HH:mm to the reviewed UTC date. Settings now displays read-only Software information, Version 1.1.2 (build 4), and Built YYYY-MM-DD HH:mm (UTC). The current debug APK was inspected with SDK 34 aapt: actual package version/code are 1.1.2/4 and the packaged software_build_timestamp equals 2026-10-24 20:59 UTC. No version-number bump, signing, application ID, credential policy or deployment workflow changed.

GenerateSoftwareInfo stamps at Gradle task execution, with application source/resources, variant BuildConfig outputs, runtime dependencies, native libraries and build recipe files as inputs. AGP registers the generated resource directory per variant. No runtime clock/install timestamp or configuration-time clock value is used. No-op debug/release invocations reported UP-TO-DATE with identical output hashes. A temporary synthetic debug resource forced regeneration; it was removed in finally and final inputs regenerated. Both generated values match their file-generation minute in UTC. This local metadata check does not build or publish a release APK.

Repeatable local checks (repository root, JDK 17): run `.gradlew.bat :app:generateDebugSoftwareInfo :app:generateReleaseSoftwareInfo --console=plain` twice. The second unchanged run should keep both generation tasks UP-TO-DATE and preserve their generated values under app/build/generated/res/generateDebugSoftwareInfo/values/software_build.xml and the corresponding generateReleaseSoftwareInfo path. Real app/build-input changes regenerate that variant; reuse of an unchanged APK retains its original stamp. Use SDK 34 aapt dump badging and dump --values resources on the debug APK to verify actual package version/code and software_build_timestamp. Do not expose other APK/private settings when reporting results.

TC-APP-150/151 verify actual Settings resource text/minute format, timezone/reentry invariance, no new focus stops, all-density/narrow-account layout and remote/Back returns. The attempt to simulate a large clock jump stalled the Robolectric UI runner and was removed; only its specifically identified local worker was stopped. Initial verification also used the wrong generated-directory assumption, corrected to AGP's actual output. These were local fixture/check issues; no product/device clock was changed. Physical reinstall/device-clock/readability acceptance remains separate.

Fresh test-smoke.ps1 -Lint passed 191/191 (151 app plus 40 shared), zero failures/skips/errors, in 93.7 seconds; debug assembly and APK inspection passed separately. This lint-including run exceeded the 90-second warm target; the two new UI cases are small, and a separate warm maintenance run is recorded below. No Firestick command, app start, installation, setting change or provider request occurred. Changes remain local/uncommitted; a separate deployment approval is still required.

## MP-1 local implementation — 24 October 2026; not deployed

The user explicitly approved REL-006 after the concrete review/in-chat overview. The shared library/legacy VOD engine now uses a 24 MiB sample-buffer target with size priority and a 10–30 second time policy. Pinned playback-start/rebuffer thresholds and Live TV's default policy remain. Quality/audio/seek/progress/identity/release behavior and the deployment script are retained.

Fresh test-smoke.ps1 -Lint passed 189/189 (149 app plus 40 shared), zero failures/skips/errors, in 85.8 seconds. Lint: zero errors/32 existing warnings. Debug assembly passed separately. Real allocator/size/release, time/start/rebuffer/live and actual player-construction checks are TC-APP-147–149. The initial fixture compile mismatch with the pinned allocator release API was corrected before these passing checks. No unresolved local errors remain.

The new debug APK is local and implementation changes remain uncommitted. No Firestick command, provider probe, app start, deployment or setting change occurred during this implementation. INV-VOD-1 remains completed. A separately approved installation and actual-source heap/seek/Resume retest are required before claiming the reproduced OOM fixed. See [MP-1](../design/VOD_MEMORY_REVIEW.md). Another deployment requires explicit approval of the retained script installing, force-stopping and starting the app, then execution of only that script.

## INV-VOD-1 completed device diagnosis — 24 October 2026

The user separately approved opening/navigating the installed app, bounded seek/Resume checks and reading diagnostics, with possible interruption/saved-position changes. Version 1.1.2 was investigated; MD-4 was not installed. No installation, force-stop, clear-data, decoder/buffer/code change or provider mutation occurred. Logging was already On and remained On.

The reported movie produced video and a seekable positive duration, but the forward request exposed a generic data-read error under current severe memory pressure. Runtime logs show roughly 64 KiB allocations failing at the 192 MiB heap limit; Resume was followed by an am_crash event explicitly identifying java.lang.OutOfMemoryError and process death. The saved point persisted at 38,175 ms. A fresh idle-app baseline used about 52.7 MiB Dalvik heap allocation; automatic video/audio buffer targets are 137.5 MiB in the pinned library, not a measured ownership breakdown. No working control was supplied. See [sanitized diagnosis](../investigations/VOD_SEEK_2026_10_24.md).

The session ended at idle Live TV browsing with no video running; initial logging remained unchanged. The user was explicitly told Firestick testing was finished. No more device actions are included in this completed session. Raw screenshots/logs/database files remain ignored/private. [MP-1 / REL-006](../design/VOD_MEMORY_REVIEW.md) was subsequently explicitly approved and locally implements the focused VOD buffer budget; installation and device retesting remain separate. The historical startup OOM/ANR evidence does not replace these current playback findings.

## MD-4 local verification — 24 October 2026; not deployed

The user approved LIB-005/LIB-006 local implementation plus VOD-009 bounded PC diagnosis. Movie categories now reuse the full fresh catalog without redundant catalog loading states and alias preparation; episode parsing preserves structural top-level IDs. Schema v3 adds parser revisions so old selected-series caches refresh atomically without clearing durable data or all-series prefetch.

Fresh test-smoke.ps1 -Lint passed 186/186 (146 app plus 40 shared), zero failures/skips/errors, in 57.7 seconds. Debug lint passed with zero errors/32 existing warnings; debug assembly passed separately. Focused fixtures also passed after correcting the migration fixture quoting and waiting for actual empty-route preparation before focus acceptance. The earlier intermittent TC-APP-136 baseline timeout remains historical evidence; its cause is not claimed fixed. No MD-4 deployment or Firestick action occurred.

The reported movie returned exact nonzero byte ranges and usable-looking MP4 index headers on PC. Its actual Android seek/resume cause is unresolved; a source/control and device diagnosis remains separate. See [sanitized PC evidence](../investigations/VOD_SEEK_2026_10_24.md) and [MD-4 including pending INV-VOD-1](../design/LIBRARY_RELIABILITY_REVIEW.md). The debug APK is built locally; no install/start or device logging change is authorized by implementation approval. Deployment requires separate explicit approval of the unchanged script's install/force-stop/start operation.

## MD-3 subsequent deployment and latest report — 24 October 2026

The user separately requested Deploy after the local MD-3 completion report. The unchanged deploy-firestick.ps1 built successfully, installed the update preserving app data and reported Installed and launched (exit 0). No flags, extra device commands or UI actions followed that invocation. The earlier local-only statements below describe their verification point, not the current installation state.

The latest user sample reports repeated movie-category loading, incomplete season/episode lists and seek/resume failure for one movie while another worked. These are unresolved under [MD-4](../design/LIBRARY_RELIABILITY_REVIEW.md), awaiting revision approval. A local compiled-parser synthetic invocation reproduces nested metadata overwriting distinct episode IDs; no affected provider series or actual movie was inspected. No further device probe, start, install, navigation or setting change has run. Physical acceptance is not complete, and the successful script start does not establish usable playback for every source.

## MD-3 local verification — 24 October 2026; not deployed

After explicit approval, all prior changes were first committed/pushed as d28c14f. MD-3 adds explicit loading/update feedback, library icons, the direct Play/watched/heart row with eligible Play-time Resume choice, durable season shortcuts in Favorites and a saved default-Off debug Verbose logging setting. Existing data and the VOD root/ANR correction are retained; the built-in database migration is additive.

Fresh test-smoke.ps1 -Lint passed 182/182 (142 app plus 40 shared), zero failures/skips/errors, in 74.6 seconds. Lint: zero errors/32 existing warnings. Debug assembly passed separately. The initial lint indentation finding was corrected and the fresh full rerun passed. No MD-3 installation, app start, provider probe, device setting change or physical acceptance occurred.

The new debug APK is built locally. MD-3 changes remain uncommitted. No unresolved local build/test/lint errors remain. See [MD-3 implementation/evidence](../design/LIBRARY_ACTIONS_REVIEW.md). To deploy, obtain separate explicit approval of the unchanged script's install/force-stop/start operation, then run only deploy-firestick.ps1. Physical checks should cover loading visibility, details/Resume/Back and icon readability, season favorite add/open/remove and Settings logging with the remote; saved logging defaults Off and need not be enabled for ordinary playback.

## Corrective deployment and latest user smoke — 24 October 2026

After the separate approval Deploy, the unchanged deployment script built successfully, installed the VOD-entry ANR correction with saved data retained and reported a successful app start (exit 0). No extra device commands or UI actions followed that deployment. The user subsequently reports no identified Live TV issues and successfully reaches Movies after an acceptable wait; the missing loading feedback is a new UX issue. This is positive sampled navigation/playback evidence, not a timed startup benchmark or complete stress/remote/Info acceptance.

The latest requested loading/icons/direct-actions/season-favorites/logging revision is [MD-3](../design/LIBRARY_ACTIONS_REVIEW.md), explicitly approved and locally implemented. The approval covers local implementation/checks; no new device session, deployment, app start or device setting change is authorized. The local-only/no-deployment statements below describe their earlier verification points.

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

### MD-4 catalog-maintenance verification

No app or implementation-test change was made for this pending proposal. The first fresh baseline smoke after the MD-4 catalog additions reported 173/182 passed, one failure (TC-APP-136, five-second wait for the watched action in a global route) and eight downstream Gradle environment checks not executed because Gradle stopped after the UI failure. This does not diagnose the user's stream/series report. An unchanged fresh rerun was requested to distinguish a repeatable failure from test timing; its outcome is recorded below. Do not describe the first run as successful or erase it if a later run passes. No device action or provider access followed the prior deployment.

The unchanged fresh rerun passed 182/182 (142 app plus 40 shared), zero failures/skips/errors, in 50.1 seconds. Generated companion rows reflect that latest execution. The earlier TC-APP-136 timeout remains evidence of intermittent local UI-test/behavior timing requiring follow-up; a passing rerun does not establish its cause or correction. No new product criteria were implemented and no feature/device/provider acceptance is inferred from the baseline pass.

Catalog-maintenance verification after recording INV-VOD-1 and the pending MP-1 proposal: fresh local smoke passed 186/186 (146 app plus 40 shared), zero failures/skips/errors, in 57.9 seconds. No app/implementation-test changes were made for MP-1; this is unchanged baseline evidence, not a memory-fix or physical acceptance result. Device testing has ended and no additional device action followed the user's status question.

After updating the related VOD-009 approval/physical-acceptance gap, a fresh catalog-maintenance smoke also passed 189/189, zero failures/skips/errors, in 56.5 seconds. These are the same 189 unique cases, not additional test coverage. The preceding lint/debug-build results remain separate; no further app or device change occurred.

SI-1 final warm maintenance: fresh smoke passed 191/191, zero failures/skips/errors, in 41.1 seconds, within the 90-second target. This is the same 191 unique cases; preceding full lint (zero errors/32 warnings), debug assembly, UTC/incremental task checks and packaged timestamp inspection are separate evidence. No further app/input/device change occurred, and the temporary synthetic source resource is absent. Physical TV/readability/clock/reinstall acceptance remains separate.
