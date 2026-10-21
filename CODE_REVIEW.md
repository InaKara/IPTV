# Aypi Tivi code review

Review date: 21 October 2026. Application/build/deployment source baseline: `7fbc654`. The uncommitted requirement-catalog documentation was left intact. This review includes both the original application and the recently added configuration/deployment code; findings are not attributed to a particular model.

**Review only. No fixes, requirement changes, dependency upgrades, commits, pushes or deployments were made for this review.** Recommendations below are proposals. Selected work must first be added to or clarified in REQUIREMENTS.md and approved by the user.

> Subsequent work: the user authorized remediation of all findings. This report retains the reviewed baseline; see [CODE_REVIEW_FIXES.md](CODE_REVIEW_FIXES.md) for the local implementation, evidence and pending device checks.

## Overall assessment

The project has useful boundaries between screens, ViewModels, playlist parsing and storage. Its size is manageable, and the existing Kotlin/Compose/ExoPlayer approach supports incremental improvement. The largest problems are unsafe focus transitions, inconsistent ownership of state and player actions, and incomplete handling of failure cases. Repeated code has already produced different behavior between paths that should follow the same rules.

There are **35 findings**, including follow-up additions CR-030–CR-035. Existing IDs are unchanged. P1 means a high-priority stability problem; P2 means a functional, reliability, privacy or validation problem; P3 means lower-priority duplication or maintainability cleanup. These are proposed remediation priorities, not approved requirements or delivery commitments.

| Category | Findings | P1 | P2 | P3 |
| --- | --- | --- | --- | --- |
| Correctness and failure handling | CR-001–CR-008, CR-032 | 1 | 8 | 0 |
| State ownership and architecture | CR-009–CR-014, CR-033–CR-034 | 0 | 4 | 4 |
| Efficiency and cancellation | CR-015–CR-016, CR-031 | 0 | 2 | 1 |
| Duplication and readability | CR-017–CR-022 | 0 | 0 | 6 |
| Configuration, tests and resources | CR-023–CR-026, CR-035 | 0 | 2 | 3 |
| Privacy and provider compatibility | CR-027–CR-030 | 0 | 4 | 0 |
| **Total** | **35** | **1** | **20** | **14** |

## Scope and validation

Reviewed all 20 application Kotlin files, the single test file, authored Gradle/build configuration, the PowerShell deployment script, manifest, resource XML, relevant documentation and the existing lint report. Generated caches/build code, the Gradle distribution/wrapper implementation and private `.env` values were not treated as authored application source.

Three defects were reproduced locally using the existing compiled application parser and OkHttp library with synthetic inputs, without contacting any provider:

| Probe | Expected | Actual |
| --- | --- | --- |
| Channel named `News, International` | Preserve the full name | Parsed as `International` |
| VOD URL ending in `video.mp4?token=sample` in an otherwise neutral category | Recognize the MP4 path as a VOD indication | `isVod = false` |
| Password `a&b#c` in the current playlist URL construction | Preserve the complete password and playlist-type query | Password parsed as `a`; playlist type absent from query |

A PowerShell probe also confirmed that its `.env` reader treats case-variant keys as duplicates, unlike Gradle's case-sensitive map. Other findings are source-derived unless otherwise stated. No new Firestick interaction, performance benchmark or complete UI test run was performed. The existing baseline had passed its single parser test and lint with 0 errors / 19 warnings; part of the Compose lint rules was skipped, as described in CR-025. Existing passing checks do not cover most cases identified here.

The user subsequently checked code-level claims against the baseline, without rerunning the original probes. This follow-up revision corrected the unused-import claim and narrowed the .env claim. New local probes confirmed the duplicate-index behavior in CR-032 and Gson's permissive missing-field/incompatible-type behavior in CR-033. The original three probes were not rerun for this revision. A strict UTF-8 read confirmed the player comment contains U+2014, not the reported mojibake sequence; the leading blank line is real. No network/provider or device tests were added during the follow-up.

## 1. Correctness and failure handling

### CR-001 — P1: focus requests can target controls that do not exist

**Locations:** [HomeScreen] lines 120–125 and 160–170; [LoginScreen] lines 164–181 and 239–242.

Right from an empty Recently viewed category requests focus on channel zero, but there is no channel node. A scrolled grid or sidebar can likewise leave the requested lazy item uncomposed. Login has the same class of problem: loading removes Connect while field-navigation handlers still target its FocusRequester. Requesting an unattached focus target can throw rather than move selection.

**Proposed correction:** give focus transitions one explicit policy: guard empty destinations, scroll lazy targets into composition before requesting focus, and keep loading controls mounted/disabled or redirect focus deliberately. Use stable item identities where focus must survive list changes. **Trace:** BROWSE-004/005, LOGIN-003/005.

### CR-002 — P2: unsuccessful HTTP responses are not closed

**Location:** [ChannelRepositoryImpl] lines 31–39.

The non-success return occurs before the response body is closed. The successful branch closes its stream, but error responses can retain connection resources during repeated failed logins.

**Proposed correction:** wrap the entire response lifetime in `use`, including status/body checks. Keep parsing inside that scope. This also removes the need to reason about cleanup at each return. **Trace:** LOGIN-005, REL-001/002.

### CR-003 — P2: credentials are interpolated into a URL without query encoding

**Location:** [ChannelRepositoryImpl] lines 26–30.

Characters such as `&`, `+` and `#` change URL/query semantics. The local probe with password `a&b#c` reproduced a truncated password and lost playlist-type parameter. This prevents valid accounts from connecting even if their credentials are entered correctly.

**Proposed correction:** parse/validate a supported HTTP(S) base URL and use OkHttp's URL builder with query parameters, instead of constructing the query as a string. Keep scheme/host/path normalization deliberate. **Trace:** LOGIN-004/005.

### CR-004 — P2: overlapping login requests can save the wrong credentials

**Location:** [LoginViewModel] lines 32–60; [LoginScreen] lines 107–157.

There is no in-flight request guard, and the editable fields/IME action remain usable while loading. A request starts with one set of strings, but its success handler saves the ViewModel's current fields after suspension. Editing fields during a slow request can therefore show channels for account A while saving account B; overlapping requests can also finish out of order.

**Proposed correction:** capture an immutable input snapshot, allow one active connection attempt or cancel its predecessor, and persist exactly the snapshot that succeeded. Define field/IME behavior while loading. **Trace:** LOGIN-005/006.

### CR-005 — P2: the parser truncates channel names containing commas

**Location:** [M3UParser] lines 39–42.

Using the last comma treats part of a legitimate display name as metadata. The actual compiled parser turned `News, International` into `International` in the local probe.

**Proposed correction:** locate the metadata/name separator outside quoted attribute values, then retain the entire remaining display name. Add targeted fixtures for commas in names and quoted attributes. **Trace:** DATA-001.

### CR-006 — P2: VOD detection examines the whole URL instead of its path

**Location:** [M3UParser] lines 60–67.

Extension checks fail when a media URL carries a query string, a common form for authenticated content. The probe with `video.mp4?token=sample` returned false. Category keyword matching can also classify a live entry as VOD just because its category contains a movie-related word.

**Proposed correction:** inspect the parsed URI path for extension hints, retain uncertainty in heuristic classification, and base seek availability on actual player capabilities rather than only this flag. **Trace:** VOD-001/003.

### CR-007 — P2: different seek controls apply different safety rules

**Locations:** [PlayerScreen] lines 120–157; [PlayerViewModel] lines 23–28.

D-pad seeking calls `seekBy`, which requires a known positive duration and clamps both bounds. Dedicated Rewind/Fast Forward bypass it. Fast Forward clamps against an unknown/negative duration, and those dedicated controls are not gated by seekability. The enabled PlayerView controller provides another route to the same player actions.

**Proposed correction:** centralize player commands and apply one policy for seekability, unknown duration and bounds, with the approved increments supplied as parameters. Review which controls the native controller should own and which Compose should intercept. **Trace:** VOD-003.

### CR-008 — P2: crash handling races the error screen against process termination

**Location:** [GlobalExceptionHandler] lines 20–31.

The handler requests an ErrorActivity in the same process and immediately kills that process. Delivery of the diagnostic screen is therefore unreliable. The original exception is neither durably saved nor passed to the default handler on this normal path, so the useful failure evidence can disappear as well. This is a source-derived race, not a newly reproduced Firestick crash.

**Proposed correction:** use a small, reliable crash-recording path, preserve normal exception reporting, and display a sanitized previous-crash report on a later safe launch if desired. Review the diagnostic UX before implementing it. **Trace:** REL-002.

### CR-032 — P2: value-based channel lookup loses the selected duplicate's position

**Location:** [MainActivity] lines 61–76; [M3UChannel] lines 3–11.

`indexOf(selectedChannel)` uses data-class equality, so selecting the second of two identical channel entries returns the first entry's index. A local probe with two equal entries followed by another channel confirmed that Next then selects the same second object again instead of advancing past it. The parser does not reject duplicate entries.

The `-1` result is also unguarded: if selection ever becomes detached from its category snapshot, Next would jump to zero and Previous to the last entry. However, current click/next/previous assignments select directly from that snapshot, so a normal path to `-1` has not been established. Recently viewed alone does not demonstrate it; its click handler also supplies the list containing the selected object.

**Proposed correction:** retain an explicit selected position or suitably distinct entry identity and validate restored/replaced selection state. Decide how duplicate playlist entries should behave; stream URL alone cannot distinguish intentionally duplicated rows. **Trace:** PLAY-002, RECENT-002; duplicate semantics need review.

## 2. State ownership and architecture

### CR-009 — P2: navigation/session state exists only in the activity's composition

**Location:** [MainActivity] lines 50–86.

Login, channel collection, selected channel and category channel list are four separate remembered values. They are not restored as navigation/session state. Activity recreation loses the playing route even if ViewModels survive; process recreation loses the whole in-memory selection. The activity also owns next/previous indexing, recent-history recording and screen switching in one conditional block.

**Proposed correction:** give session/navigation state a clear owner and a small explicit screen/selection model. Save appropriate identifiers and playback position according to an approved restore policy; do not put the complete playlist into a saved-state Bundle. **Trace:** BROWSE-007 and a restore-policy decision before broadening scope.

### CR-010 — P2: playback cleanup follows composition, not the app lifecycle

**Locations:** [PlayerScreen] lines 89–94; [PlayerViewModel] lines 84–92.

The player is released when leaving composition or when its ViewModel is cleared. Pressing the system Home button can stop the activity while preserving both, leaving no explicit policy to pause/release streaming or resume it later. Flow collection also uses plain `collectAsState` throughout the screens. These are lifecycle gaps; the precise Firestick background effect was not tested in this review.

**Proposed correction:** first decide the foreground/background playback policy, then make player ownership and observation lifecycle-aware. A background playback service is a separate product decision, not an automatic refactor. Android documents lifecycle-aware Compose collection in its [state guidance](https://developer.android.com/develop/ui/compose/state). **Trace:** REL-003/PLAY-005 plus a new explicit lifecycle requirement if selected.

### CR-011 — P3: the login form maintains two copies of every input

**Locations:** [LoginScreen] lines 66–68, 109, 127 and 145; [LoginViewModel] lines 28–30.

Each field is stored in both local Compose state and ViewModel state, and every edit manually updates both. The copies currently tend to move together, but any later reset, validation or programmatic update must keep them synchronized. This is unnecessary state and repeated assignment.

**Proposed correction:** choose one owner for form inputs and send edits through a small event interface. Keep the display directly derived from that state. **Trace:** LOGIN-001/006; behavior-preserving cleanup proposal.

### CR-012 — P3: UI rendering is coupled to Hilt and connection state machinery

**Location:** [LoginScreen] lines 59–63 and 248–255; the same screen/ViewModel pattern exists in Home and Player.

The login preview calls the production screen, which obtains a Hilt ViewModel by default. A normal design preview has no configured production Hilt environment, making preview rendering unreliable. UI tests must also construct more machinery than the visual form needs.

**Proposed correction:** retain a route/controller composable that obtains the ViewModel, and extract a rendering composable receiving state and callbacks. Previews can supply synthetic data. Do not add an additional architectural framework solely for this separation. **Trace:** UI-001/LOGIN-001 and testability cleanup.

### CR-013 — P3: player state names do not reflect actual playback state

**Locations:** [PlayerViewModel] lines 31–35 and 64–72; [PlayerScreen] lines 166–181.

`Playing` is emitted on STATE_READY, which also applies when paused. Ended and idle transitions only write logs, leaving the old state behind. The player reference itself is not observable; initial view binding is refreshed indirectly when another UI-state event causes recomposition.

**Proposed correction:** expose only state the UI actually needs, with accurate readiness/playing/error semantics, and make player/view attachment explicit. Avoid maintaining a second incomplete version of ExoPlayer's state machine. **Trace:** PLAY-001/004/005.

### CR-014 — P2: recent history is not reconciled with the active provider/playlist

**Locations:** [RecentlyWatchedManager] lines 21–42; [HomeViewModel] lines 51–54.

Complete old channel objects, including stream URLs, are read straight into Recently viewed. Switching provider or account does not scope/filter/re-resolve them against the new playlist. Old authenticated URLs can remain selectable and fail even when the new provider login works.

**Proposed correction:** agree how history should behave across provider/account changes, then scope or resolve recent entries against the active source using a suitable identity. Do not blindly rewrite authenticated URLs. **Trace:** RECENT-002/003, SET-004; cross-provider history behavior needs requirement review.

### CR-033 — P2: recent-history JSON has no schema or migration policy

**Locations:** [RecentlyWatchedManager] lines 34–44; [M3UChannel] lines 3–11.

Stored history is a direct Gson serialization of the application channel model, with no version, migration or post-read validation. An incompatible field type can cause parsing to throw, which the manager converts silently into an empty list; the next history addition would overwrite the old entries. Other model changes do not necessarily empty history: Gson can ignore extra fields or supply defaults/nulls for missing fields, including Kotlin non-null properties. Local probes confirmed that a missing streamUrl is accepted as null and an object where a string is expected throws a parse error.

**Proposed correction:** define a versioned stored representation, validate required fields and specify migration/recovery behavior. Do not claim that every model change loses history, and do not silently overwrite recoverable data after a failed read. **Trace:** RECENT-003; evolution/recovery policy needs review.

### CR-034 — P3: the repository constructs its own HTTP client

**Location:** [ChannelRepositoryImpl] lines 13–21; [AppModule] lines 25–27.

The injected repository receives a parser but builds its own concrete OkHttpClient. This hides timeouts and network configuration inside data access and prevents straightforward injection of a controlled client for tests or provider-specific policies. Because the repository is currently provided as a singleton, this is not evidence that a client is created for every request, or that repeated connection pools are being leaked.

**Proposed correction:** provide a configured client through dependency injection and inject it into the repository. Keep player/image clients separate where their policies differ rather than forcing all network traffic through one global client. **Trace:** CR-019, LOGIN-004/005; architecture/testability cleanup.

## 3. Efficiency and cancellation

### CR-015 — P2: returning to browsing starts duplicate full-playlist work

**Locations:** [HomeScreen] lines 64–71; [HomeViewModel] lines 29–54.

Both effects refresh content, and `loadChannels` already calls `refreshContent`. On entry/return this can launch the same grouping/sorting work twice. Each refresh rebuilds all category lists even if only the small recent-history list changed. The work is off the main thread but still consumes CPU and allocation on the Firestick.

**Proposed correction:** use one initialization/refresh path, cache category grouping until the playlist changes, and update recent history separately. Make overlapping refresh results explicit rather than launching unconstrained work. Hoisting the fixed VOD keyword list out of the parser's per-channel function is a smaller related allocation cleanup. Performance benefit should be measured, not assumed. **Trace:** REL-001, RECENT-003.

### CR-016 — P2: coroutine cancellation is not connected to the HTTP operation

**Locations:** [ChannelRepositoryImpl] lines 24–44; [LoginViewModel] lines 41–60.

The repository runs synchronous `execute()` on an IO dispatcher, without a cancellation hook to the OkHttp call. Leaving/cancelling the coroutine does not promptly stop that network operation. Broad Exception handlers at both layers can also convert coroutine cancellation into an ordinary login error, and duplicate failure-handling logic.

**Proposed correction:** use a cancellation-aware call bridge, rethrow CancellationException, and give expected network errors one owner. Retain IO dispatch for blocking parsing if needed. This supports the single-flight login policy in CR-004. **Trace:** LOGIN-005, REL-001/002.

### CR-031 — P3: each channel selection performs JSON work on the UI thread

**Locations:** [MainActivity] lines 57–58; [RecentlyWatchedManager] lines 21–42.

The LaunchedEffect calls a synchronous addChannel without changing dispatchers. The preference lookup, Gson decoding, list manipulation and Gson encoding therefore run on the main thread on every selection/switch. SharedPreferences.apply schedules the disk write asynchronously, and reads are normally served from its in-memory cache after loading; it would be inaccurate to describe this as a synchronous disk read/write on every change.

The history is capped at 20 entries, so no significant frame-time cost has been measured or established. This is bounded but avoidable main-thread work, rated P3 rather than a demonstrated playback-performance defect.

**Proposed correction:** maintain a small in-memory history state and serialize/persist off the UI thread, preserving ordered updates and avoiding concurrent read-modify-write races. Measure the effect on the target device before adding more elaborate storage machinery. **Trace:** REL-001, RECENT-002/003.

## 4. Duplication and readability

### CR-017 — P3: SeekOverlay is defined twice, including an unused local copy

**Location:** [PlayerScreen] lines 43–74 and 190–221.

An effectively identical local composable is embedded halfway through the player content, after the existing call to the top-level version. The local declaration is not invoked there. It adds roughly 30 lines of dead duplication and obscures where the player's Box content resumes. This is the clearest copy/paste artifact in the app.

The file also starts with a stray blank line. The reported `â€”` encoding artifact was not found in the source: a strict UTF-8 read of the overlay comment shows a valid U+2014 em dash. If it displays as mojibake in a particular terminal/editor, check that reader's encoding before changing the source text.

**Proposed correction:** keep one private implementation, remove the unused local copy and restore consistent layout/indentation. **Trace:** VOD-005; behavior-preserving cleanup proposal.

### CR-018 — P3: transient overlays use display text as event identity

**Location:** [PlayerScreen] lines 45–51, 96 and 120–130.

Writing the same `+30 s` value again does not change Compose state, so it does not restart the effect. After the overlay expires, a second identical seek may produce no feedback. Channel-name feedback has the analogous limitation when distinct streams have the same displayed name. The same show-delay-hide pattern is repeated for the overlays.

**Proposed correction:** give each notification an event identity/sequence and use one small timed-overlay helper where useful. Use the stream/channel identity when restarting channel feedback. **Trace:** VOD-005, UI-006.

### CR-019 — P3: dependency construction uses two overlapping mechanisms

**Locations:** [AppModule] lines 21–36; injectable constructors in [M3UParser], [CredentialsManager], [RecentlyWatchedManager] and [ChannelRepositoryImpl].

Classes have injectable constructors while the module manually constructs them. This is legal, but leaves redundant factory declarations and hides missing context qualifiers behind manual providers. Repository interface binding is also written as a construction factory.

**Proposed correction:** use qualified constructor injection for suitable concrete classes and an interface binding for the repository. Keep providers for objects that genuinely require builder configuration, such as ImageLoader or a configured HTTP client. **Trace:** architecture cleanup proposal; no new DI framework required.

### CR-020 — P3: repeated form UI and scattered display constants increase editing cost

**Location:** [LoginScreen] lines 93–159 and 202–221; related labels/timings across [MainActivity], [PlayerScreen] and [SplashActivity].

Three nearly identical input-field blocks repeat width, focus/key behavior, colors and spacing. User-visible text and timing/seek/grid constants are scattered through screen implementations; most text bypasses string resources. The login spinner is custom drawing/animation code despite an existing UI component library.

**Proposed correction:** extract a small reusable TV field, centralize repeated behavioral constants and strings, and evaluate a standard themed progress indicator. Keep genuinely different screen layouts explicit rather than creating a generic UI framework. **Trace:** UI-002, LOGIN-001/003; any visible change requires review.

### CR-021 — P3: unused imports, declarations and dependency intent need cleanup

**Locations:** [HomeScreen] imports and [HomeViewModel] lines 80–82; [LoginScreen] imports; [App build] lines 105–122; [Root build] line 4.

Examples include unused Home BackHandler/coroutine imports, an unused HomeViewModel TAG, and the unused TextField import in LoginScreen. LoginScreen's Icons and PlayArrow imports are used by the loading indicator and must be retained; the original review incorrectly included icon imports in the unused examples. The logging-interceptor dependency is not configured anywhere. There is no NavHost/NavController despite a direct navigation-compose declaration; check its necessity while accounting for Hilt's navigation-compose dependencies. Explicit animation versions duplicate the Compose BOM's responsibility, and a library plugin is declared without a library module.

**Proposed correction:** run focused unused-code cleanup, inspect resolved dependencies, and remove only genuinely unnecessary declarations. Keep direct declarations for APIs actually used; a transitive dependency is not automatically a reason to delete its declaration. **Trace:** OPS-004/maintainability.

### CR-022 — P3: clearing credentials also clears unrelated recent history

**Locations:** [CredentialsManager] lines 9 and 34–35; [RecentlyWatchedManager] line 13.

Both managers use the same preferences file, but CredentialsManager.clear clears every key in it. A future logout/reset caller could unexpectedly erase recent history. This method currently has no caller, so it is a latent API design problem rather than an observed user-data loss.

**Proposed correction:** clear only the owned credential keys, or expose an intentionally named all-app-data reset operation with reviewed behavior. Share the preferences-file declaration explicitly if it remains shared. **Trace:** LOGIN-006, RECENT-003; data-clearing semantics require review.

## 5. Configuration, tests and resources

### CR-023 — P3: duplicate-key case policy differs between the .env readers

**Locations:** [App build] lines 10–26; [Deploy script] lines 11–34.

The literal parser is separately implemented in Kotlin and PowerShell. Both permit uppercase and lowercase letters in key names. PowerShell already uses the case-sensitive regex operator `-cnotmatch` for syntax validation; the mismatch is its default case-insensitive hashtable versus Kotlin's case-sensitive linked map, not regex validation. A local probe confirmed that `IPTV_USERNAME` plus `iptv_username` is rejected as a duplicate by the script, while Gradle's map can hold both and its uppercase lookup selects only the uppercase key. This is a narrow inconsistency for case-variant keys, not evidence that ordinary correctly named entries or literal values generally parse differently. It concerns the recently added tooling.

**Proposed correction:** adopt one explicit key-case policy, such as rejecting noncanonical names consistently or using case-sensitive dictionaries in both readers, and test it with shared synthetic fixtures. Keep literal parsing and do not introduce shell evaluation. **Trace:** SET-005.

### CR-024 — P2: regression coverage is too narrow for the app's fragile behavior

**Location:** [Parser test] lines 10–35; there are no other versioned app tests.

One happy-path parser test leaves comma/attribute edge cases, VOD detection, query encoding, login concurrency, focus/navigation, history migration and player actions unchecked. The temporary configuration checks performed during recovery are not a reproducible versioned regression suite. Existing defects survived a passing baseline.

**Proposed correction:** add targeted tests for selected approved fixes, especially the reproduced parser/URL defects and state transitions; use device/UI checks for remote focus and player lifecycle. Test outcomes and failure cases rather than mirroring getters or implementation structure. **Trace:** validation work should accompany the selected requirement IDs.

### CR-025 — P2: a successful lint run currently skips a set of Compose checks

**Location:** existing local lint report, `ObsoleteLintCustomCheck`; dependency setup in [App build] and [Root build].

The report explicitly excludes a Compose runtime issue registry because of incompatible lint APIs. Affected rules include state creation, remembered state, coroutine creation during composition and other Compose checks. Consequently “0 lint errors” provides less assurance than its summary suggests. The other warnings also expose an aging dependency baseline, but newer versions alone are not evidence of an app bug.

**Proposed correction:** plan a compatible tooling/dependency alignment and verify that the skipped rules run again. Update in reviewed, testable steps rather than treating every available upgrade as urgent. **Trace:** OPS-004 and a tooling-maintenance requirement if selected.

### CR-026 — P3: branding assets have multiple competing representations

**Locations:** [SplashActivity] lines 65–83; [Launch background] lines 6–25; [Manifest] lines 15–16; `app_logo.png`.

The custom splash uses the chosen image, the initial window background uses a generic play symbol with a transparent rectangle where its comment claims a name, and the app icon/banner uses another generic TV vector. The lint report also confirms that app_logo.png is actually JPEG data and is stored in a density-unspecified drawable folder.

**Proposed correction:** agree on one branding asset set and the intended splash/launcher variants, then correct file format/extension and resource placement. The explicit two-second custom splash delay is also a UX choice to review before changing launch speed. **Trace:** UI-001/003/004.

### CR-035 — P3: release builds have no code/resource optimization policy

**Location:** [App build] lines 67–69.

The release build explicitly disables minification and does not enable resource shrinking. Unused application/dependency code can therefore remain in release artifacts. This is a release-efficiency/maintenance decision, not a defect in the currently working debug deployment, and obfuscation would not make embedded secrets secure.

**Proposed correction:** agree whether a distribution-ready release is in scope, then evaluate R8/resource shrinking with appropriate rules and release tests, including Gson persistence and playback. Do not enable optimization blindly during a behavior-preserving cleanup. **Trace:** OPS-004 and an explicit release-distribution requirement if selected.

## 6. Privacy and provider compatibility

### CR-027 — P2: private device preferences are eligible for backup without an explicit policy

**Locations:** [Manifest] line 13; [CredentialsManager] and [RecentlyWatchedManager].

Credentials and complete stream URLs are stored unencrypted in app-private preferences, while allowBackup is true and no backup exclusion rules are supplied. On systems where backup is enabled, this makes the private data eligible for backup/restore. App-private does not mean world-readable; this finding concerns the missing backup/storage policy, not a claimed unauthenticated extraction vulnerability.

**Proposed correction:** review which private data may be backed up, exclude sensitive preferences as appropriate, and decide whether stronger at-rest protection is needed. Repository .env exclusion already addresses a different, correctly handled boundary. **Trace:** PRIV-001/003 plus a separate approved device-storage policy.

### CR-028 — P2: diagnostic output has no sanitization boundary

**Locations:** [LoginViewModel] lines 55–60; [PlayerViewModel] lines 59–61; [HomeScreen] logo-error callback; [GlobalExceptionHandler] lines 16–22.

Raw exceptions, logo URLs and complete crash stacks are logged or displayed. Provider URLs can carry usernames, passwords or access tokens, so a provider error or shared screenshot/log can disclose private information even though source control is clean. The recent removal of direct account/stream startup logs reduced exposure but did not establish a general policy.

**Proposed correction:** separate safe user messages from diagnostic details, sanitize authenticated URLs, and define debug/release logging rules. Do not assume every error includes secrets, or remove all useful diagnostics indiscriminately. **Trace:** PRIV-001 and an explicit diagnostic-privacy requirement if selected.

### CR-029 — P2: parsed provider capabilities are not carried through to playback

**Locations:** [M3UParser] line 35; [MainActivity] lines 62–65; [PlayerViewModel] lines 49–80; [App build] lines 124–127.

The parser retains user-agent metadata, but PlayerScreen receives only URL, name and a VOD flag, so required request headers cannot reach the player's data source. The build also lacks HLS/DASH/RTMP extension modules; a playlist being M3U does not mean every stream inside it is supported. This is a conditional compatibility gap, not proof that the currently working provider needs every protocol.

**Proposed correction:** establish the actual required stream formats/headers, then pass a suitable playback description and add only required player/data-source support. Media3 documents protocol-specific module selection in its [getting-started guide](https://developer.android.com/media/media3/exoplayer/hello-world). **Trace:** DATA-004, PLAY-001/006; protocol scope needs confirmation.

### CR-030 — P2: HTTP connections expose account credentials in transit

**Locations:** [Manifest] line 19; [ChannelRepositoryImpl] lines 26–30.

The manifest permits cleartext traffic, the repository chooses HTTP for a scheme-less address, and the provider username/password are sent as query parameters. When HTTP is used, the request is not encrypted in transit. Query encoding (CR-003), private local configuration and encrypted storage would not protect that network request. No network interception or provider connection was performed to establish this source-level finding.

**Proposed correction:** prefer verified HTTPS where the provider supports it and make any necessary HTTP-only exception an explicit reviewed compatibility/risk decision. Do not silently downgrade a failed HTTPS connection, or enforce a change that breaks an HTTP-only provider without user review. **Trace:** LOGIN-004, PRIV-001/003; transport policy needs an explicit requirement separate from backup and logging.

## Existing product decisions that need review rather than automatic cleanup

The requirement catalog already identifies several behavior mismatches. They should not be hidden inside a refactor:

- **PLAY-003:** current Up = next and Down = previous differs from the historical Down = next request.
- **BROWSE-006:** Back in the channel grid currently triggers the global exit-confirmation handler rather than first returning to category selection.
- **LOGIN-003:** explicit Select handling was added, but suppressing keyboard activation during ordinary focus navigation is not an explicit text-field policy and needs a real remote test.
- **VOD-004:** Stop semantics are undefined and no Stop command is implemented.

These are already pending catalog review; they are not counted again among the 35 code-quality findings.

## Proposed order if remediation is selected

1. Stabilize focus and failure paths: CR-001–CR-008 and duplicate-position handling in CR-032, with focused regression checks.
2. Agree session/background/history behavior, then simplify state ownership: CR-009–CR-016, CR-033/034, and measured UI-thread storage cleanup in CR-031.
3. Remove dead duplication and consolidate repeated behavior without altering approved UX: CR-017–CR-023 and branding decisions in CR-026. Review release optimization separately under CR-035.
4. Make validation reproducible and align lint/tooling: CR-024/025. Add targeted tests alongside earlier fixes rather than waiting until this stage to test them.
5. Review transport/device privacy and actual provider capabilities: CR-027–CR-030. Prioritize earlier if the user plans to distribute builds, requires currently unsupported streams, or can move an HTTP connection to supported HTTPS.

Readability cleanup can accompany an approved fix in the same area, but unrelated migrations or new features should remain separate. No findings have been marked accepted, assigned new requirement IDs, or implemented by this report.

## Source references

File links below and above refer to the reviewed local baseline; line numbers are stated with each finding. The external Android references support lifecycle/state and module guidance; the application-specific findings come from this repository and the stated probes.

[HomeScreen]: app/src/main/java/com/example/iptv/ui/home/HomeScreen.kt
[LoginScreen]: app/src/main/java/com/example/iptv/ui/login/LoginScreen.kt
[HomeViewModel]: app/src/main/java/com/example/iptv/ui/home/HomeViewModel.kt
[LoginViewModel]: app/src/main/java/com/example/iptv/ui/login/LoginViewModel.kt
[MainActivity]: app/src/main/java/com/example/iptv/MainActivity.kt
[SplashActivity]: app/src/main/java/com/example/iptv/SplashActivity.kt
[PlayerScreen]: app/src/main/java/com/example/iptv/ui/player/PlayerScreen.kt
[PlayerViewModel]: app/src/main/java/com/example/iptv/ui/player/PlayerViewModel.kt
[ChannelRepositoryImpl]: app/src/main/java/com/example/iptv/data/repository/ChannelRepositoryImpl.kt
[M3UParser]: app/src/main/java/com/example/iptv/data/parser/M3UParser.kt
[CredentialsManager]: app/src/main/java/com/example/iptv/data/storage/CredentialsManager.kt
[RecentlyWatchedManager]: app/src/main/java/com/example/iptv/data/storage/RecentlyWatchedManager.kt
[M3UChannel]: app/src/main/java/com/example/iptv/data/model/M3UChannel.kt
[AppModule]: app/src/main/java/com/example/iptv/di/AppModule.kt
[GlobalExceptionHandler]: app/src/main/java/com/example/iptv/util/GlobalExceptionHandler.kt
[Parser test]: app/src/test/java/com/example/iptv/data/parser/M3UParserTest.kt
[App build]: app/build.gradle.kts
[Root build]: build.gradle.kts
[Deploy script]: deploy-firestick.ps1
[Manifest]: app/src/main/AndroidManifest.xml
[Launch background]: app/src/main/res/drawable/splash_background.xml
