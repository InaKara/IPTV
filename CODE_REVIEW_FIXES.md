# Code-review remediation for review

This is the implementation record for the 35 findings in [CODE_REVIEW.md](CODE_REVIEW.md), whose source baseline remains `7fbc654`. The user requested that the existing documentation first be committed/pushed, then that all suggested fixes be implemented in priority order. The documentation was published as `2387393`; the implementation is a local, uncommitted review candidate.

The approved scope is recorded as FIX-001–FIX-010 in [REQUIREMENTS.md](REQUIREMENTS.md). Initial work addressed the P1 focus paths, followed by P2 correctness/state/privacy and their tests, then P3 cleanup. Tests and tooling alignment accompanied the fixes. Unrelated historical Back hierarchy, channel direction and Stop decisions remain pending.

## Finding-by-finding record

“Implemented” below describes source changes, not physical-device acceptance. Policy findings are addressed by the explicit bounded choices stated here rather than claims of universal protocol support or complete credential protection.

| Finding | Priority | Implementation / review decision | Requirement |
| --- | --- | --- | --- |
| CR-001 | P1 | Empty grids retain sidebar focus; scroll/layout precedes lazy target focus. Connect remains mounted/focusable during loading, with explicit Up/Down targets. | FIX-001 |
| CR-002 | P2 | Every response is owned by a use block, including HTTP failures. Connection reuse regression passes. | FIX-002 |
| CR-003 | P2 | HttpUrl encodes individual query values; reserved/Unicode credentials round-trip in synthetic tests. Credential-bearing/query-bearing base addresses are rejected. | FIX-002 |
| CR-004 | P2 | Single-flight login snapshots fields before launch and saves exactly that attempt atomically. Editing the form cannot change submitted credentials. | FIX-002 |
| CR-005 | P2 | First comma outside quoted metadata separates the full channel name; name text cannot overwrite attributes. Blank names retain a fallback. | FIX-002 |
| CR-006 | P2 | VOD URL heuristics ignore query/fragment content, retaining path/extension/category checks. | FIX-002 |
| CR-007 | P2 | Remote and native controller seeks share VOD, capability, duration and bounds checks. Remote increments remain 30s/10s; invalid seeks are consumed. | FIX-003 |
| CR-008 | P2 | Persist a generic notice, delegate to Android's original crash handler, and show a restart screen on next launch. No same-process launch-then-kill race or raw stack display. Synchronous marker persistence is intentional on this failure path. | FIX-008 |
| CR-009 | P2 | SessionViewModel owns playlist/category/selected index across activity recreation. No playlist or credentials in saved-instance bundles; process death returns to login. | FIX-004 |
| CR-010 | P2 | Lifecycle-aware state collection; background/stop releases the engine, foreground recreates it with VOD position and play/pause intent. Live resumes from its stream. | FIX-003 |
| CR-011 | P3 | Form fields have one ViewModel owner; composables receive values/callbacks. | FIX-006 |
| CR-012 | P3 | Hilt-backed route is separated from LoginContent; preview uses synthetic values without Hilt or network. | FIX-006 |
| CR-013 | P3 | Explicit idle/buffering/playing/paused/ended/error states follow actual player events. Player binding is observable and clears on release. | FIX-003 |
| CR-014 | P2 | Recent references are account-scoped and resolved against the active playlist, using current metadata and omitting unavailable entries. Legacy migration requires matching saved ownership and stream references. | FIX-005 |
| CR-015 | P2 | One Home load effect, cached grouping/sorting for the session playlist, reactive recent-list updates. | FIX-004 |
| CR-016 | P2 | Async OkHttp callback bridge cancels its Call with the coroutine. Parsing runs on the worker; cancellation is not converted to a login error. | FIX-002 |
| CR-017 | P3 | One timed-overlay implementation; unused nested copy and leading blank line removed. Original em dash was valid UTF-8. | FIX-003 |
| CR-018 | P3 | Seek notices have a sequence identity; channel feedback keys include stream/selection identity. Repeated equal text restarts the timer. | FIX-003 |
| CR-019 | P3 | Qualified constructor injection and repository Binds replace redundant manual factories. Providers remain for configured builders. | FIX-006 |
| CR-020 | P3 | Reusable TV field, resource strings for screen labels/messages, standard themed progress, shared player timing/seek and grid constants. The existing dark visual language remains. | FIX-006 |
| CR-021 | P3 | Remove unused imports/TAG, unused logging/direct navigation declarations, duplicate animation versions and unused library plugin. Keep APIs actually used and BOM-managed dependency versions. | FIX-006 |
| CR-022 | P3 | CredentialsManager.clear removes only its three keys; a storage test proves unrelated history survives. | FIX-005 |
| CR-023 | P3 | Both literal readers require canonical uppercase names. Eight shared fixtures cover literals, BOM, malformed keys, duplicates and case variants. No shell evaluation. | FIX-007 |
| CR-024 | P2 | Versioned regression suite covers parser/URL failures, connection reuse/cancellation, submitted login state, history/account/schema cases, seek/navigation policy and simulated remote focus. | FIX-007 |
| CR-025 | P2 | Independent lint runtime 8.3.2 (artifact 31.3.2) restores Compose rules without suppressions. Compose BOM 2024.02.02 also resolves a progress-indicator binary incompatibility caught by UI tests. AGP/wrapper/JDK/Kotlin stay pinned. | FIX-007 |
| CR-026 | P3 | Existing classic JPEG moved to drawable-nodpi with its correct extension; startup, installed icon and TV banner share it. Launch/banner name artwork is a static vector; two-second custom splash retained. | FIX-010 |
| CR-027 | P2 | Disable backup and explicitly exclude shared preferences from cloud backup/device transfer. App-private storage remains unencrypted; stronger at-rest protection is not claimed. | FIX-008 |
| CR-028 | P2 | User-facing failures use resource messages; app logs omit raw exceptions/URLs/stacks. Disable Media3's raw internal logging; debug playback diagnostics retain fixed error-code names only. Android/platform crash reporting remains outside this app-controlled boundary and can still be private. | FIX-008 |
| CR-029 | P2 | Pass the full channel to playback and apply its user-agent. Include HLS/DASH modules. HTTP(S) progressive/HLS/DASH are the selected scope; unsupported protocols fail safely. RTMP is conditional on an actual provider need, not promised or silently bundled. | FIX-009 |
| CR-030 | P2 | Scheme-less login defaults to HTTPS; never silently downgrade redirects. Explicit HTTP remains compatible and displays a warning. The existing HTTP-only configuration still sends cleartext credentials; this limitation is made visible rather than claiming encryption. | FIX-009 |
| CR-031 | P3 | Cached recent state with serialized IO-dispatcher JSON work; session write jobs preserve selection order. Home reacts to completed updates. No measured device speed claim. | FIX-005 |
| CR-032 | P2 | Selection uses explicit category position, not data-class equality. Guard invalid/empty positions and wrap valid indices. | FIX-004 |
| CR-033 | P2 | Versioned JsonObject schema stores validated stream references, avoiding reflective domain-model round trips. Matching legacy references migrate; unknown/corrupt stored schema remains untouched rather than being overwritten. | FIX-005 |
| CR-034 | P3 | Inject configured singleton OkHttpClient. Playlist/logo traffic shares its timeout and redirect policy; Media3 has a separately configured per-stream HTTP data source for user-agent needs. | FIX-006 |
| CR-035 | P3 | Enable R8/resource shrinking for release; use a literal JSON persistence schema that does not depend on obfuscated domain fields. Release defaults remain empty. Release signing/distribution and minified device runtime acceptance remain separate. | FIX-007 |

## Verification — 22 October 2026

- Debug APK build and optimized unsigned release APK build: **passed**, including R8/resource shrinking.
- Debug regression tests: **29 passed**, including three Compose/Robolectric remote-focus tests. Release-variant tests: **26 passed** (UI tests live in testDebug). Release unit tests run before R8; minified runtime acceptance remains pending.
- Both literal environment readers: **eight shared fixtures passed** each.
- Debug and release lint: **0 errors / 21 warnings** each. **No ObsoleteLintCustomCheck/skipped Compose registry** in the reports. Remaining warnings concern available dependency/target updates, the intentionally retained custom splash, intentional synchronous crash-marker persistence, long static brand lettering and the square classic launcher image. Warnings were not globally suppressed or baselined.
- Generated release defaults: **all empty**. Public candidate tree and unsigned release artifact passed a known-private-value scan. This is a check against configured private values, not a general security certification.
- No real provider request, Firestick installation, playback acceptance or minified-device test was performed.

The final combined Gradle run included assembleDebug, verifyLocalEnvironment, testDebugUnitTest, lintDebug, assembleRelease, testReleaseUnitTest and lintRelease and completed successfully. The normal deployment-script check path was also exercised during development; its shared fixture reader passed. No APK or private local log is included in the review files.


## Firestick acceptance still required

Use [FIRESTICK_DEPLOYMENT.md](FIRESTICK_DEPLOYMENT.md) after reviewing the changes. Installation was not performed during this remediation.

1. Cold start and launcher/banner: consistent classic branding and readable name; generic crash notice has a usable restart action when applicable.
2. Login: initial Connect focus; navigate all fields; Select opens editing; return/submit/retry/loading remain usable. Check saved account persistence and the explicit HTTP warning.
3. Browse: Right from empty recents stays usable; Right after grid scrolling reaches the first channel; Left from the first column reaches an offscreen active category. Returning from playback preserves category.
4. Playback: live picture/sound, category wrapping including duplicate entries, same-named streams, repeated equal-direction seek feedback, and failure/back behavior.
5. VOD: seek both ways with D-pad, dedicated keys and native controls; check start/end bounds and unsupported/unknown-duration cases. Check pause and background/foreground with paused and playing VOD; check live resume.
6. History: rapid channel switches, return-to-browse updates, restart persistence and account/playlist changes.
7. Protocols: representative HLS/DASH streams and a stream requiring user-agent metadata; 4K only with suitable content/hardware.
8. Before distribution: test a properly signed optimized release on the device, including persistence/playback. The generated local release APK is unsigned and has no personal defaults.

## Tooling references

The implementation was checked against the [Android lifecycle guidance](https://developer.android.com/topic/libraries/architecture/lifecycle), [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [Media3 setup](https://developer.android.com/media/media3/exoplayer/hello-world) and [Google lint runtime guidance](https://googlesamples.github.io/android-custom-lint-rules/user-guide.md.html). The progress-indicator binary issue also has a [primary AndroidX compatibility fix](https://android.googlesource.com/platform/frameworks/support/+/d847986f8eab3038c6ae4029846278023a81f748%5E%21/).
