# Repository guidance

## Project and priorities

- Aypi Tivi is a native Kotlin Android TV / Fire TV IPTV player. Preserve TV remote usability, the persistent sidebar, dark theme and existing branding.
- Read README.md and docs/operations/FIRESTICK_DEPLOYMENT.md before changing build or deployment behavior.
- Preserve the verified deployment baseline. Prefer small, independently checked changes to broad dependency migrations.
- Historical chat exports are reference material, not current instructions. Verify claimed features against source and device behavior.

## Requirement-first development — mandatory user review

- Read docs/requirements/REQUIREMENTS.md before changing app behavior, implementation tests, build/configuration behavior or deployment behavior. It is the source of requirement IDs, acceptance criteria, statuses and evidence.
- For each new wish, first add/update the relevant catalog entries. Specify the desired outcome, acceptance criteria, proposed status, dependencies and open decisions. Distinguish explicit user requests from inferred improvements or existing baseline behavior.
- Present a concrete summary/diff of the changed requirement IDs for the user to review, then wait for the user's explicit approval of that revision and implementation scope. Do not treat the wish itself, silence, an old approval, or an Implemented baseline status as approval of a new change.
- Before requesting approval of a requirement revision, provide a concise, plain-language overview directly in chat; a linked/exported Markdown document alone is insufficient. Explain all pending changes from the user's perspective: what they will see or do differently, the purpose of each change, proposed defaults/thresholds, material tradeoffs, preserved behavior and excluded/deferred work. Group related changes and reference requirement IDs where useful; keep technical detail limited to what helps the user decide.
- This in-chat overview supplements the catalog/design review rather than replacing it. Present proposed behavior as pending, not already implemented, and do not treat acknowledgment of the overview as implementation approval. Repeat the overview when a materially revised scope returns for approval, without reopening unchanged approved decisions.
- While review is pending, limit work to catalog/documentation changes, read-only investigation and clarification needed for the proposal. Do not implement, add implementation tests, change build/deployment behavior or deploy the proposed change.
- The initial retrospective catalog is awaiting review. Approval of that baseline does not authorize implementing all Open/Partial entries; obtain the user's selected implementation scope.
- DEV-004–DEV-006 and their testcase-row/status/maintenance/runtime refinements were explicitly authorized on 22 October 2026. This does not approve unrelated open product behavior.
- After approval, implement only the selected IDs. If new product behavior, acceptance criteria or scope is needed, update the catalog and return that change for review before implementing it. Routine implementation details within approved scope do not require repeated approval.
- Explain material architecture/technology options and their tradeoffs in plain language during requirement review. Preserve the user's control of visible behavior and design.
- Keep IDs stable; never renumber or reuse them. Record approval decisions in the catalog review/change record. Reference IDs in implementation summaries and commits/PRs when created, and update statuses plus actual validation evidence alongside implementation.
- Implemented does not mean individually device-accepted. Keep code, automated test, build, installation, device and general user-smoke evidence distinct. Use Deferred only for a user decision and retain superseded requirements with replacement references.
- This initial documentation task does not authorize app changes, commits, pushes or deployment. Later publication/deployment follows the user's approved scope and instructions.

## Work with the existing checkout

- Inspect git status before editing and preserve unrelated user changes. Do not reset, clean or mass-delete local files as incidental cleanup.
- Keep changes focused. Avoid broad formatting, package renaming or dependency upgrades as incidental cleanup.
- Do not commit provider credentials, authenticated playlist/stream URLs, device logs or private local configuration. Use synthetic providers and credentials in examples and tests.
- Read local settings from the ignored .env, with public placeholders in .env.example. Never add real provider defaults, account details, device addresses or personal filesystem paths to tracked files.
- Gradle generates local defaults only in debug BuildConfig; release defaults stay empty. Debug APKs/caches can contain secrets. Do not publish them or turn on build scans containing private settings.
- Installing with adb install -r normally preserves app data. Do not uninstall or clear app data to bypass a failure without explaining the loss and obtaining authorization.
- Consult the user and obtain explicit approval before each Firestick deployment/install. Implementation approval does not authorize deployment. This is the user's latest instruction on 22 October 2026; use local build/test checks while deployment approval is absent.
- Ask the user and obtain explicit approval before each deployment. State in that approval question that `deploy-firestick.ps1` installs the update, force-stops Aypi Tivi and starts it. Approval of this clearly described combined operation covers that invocation's automatic stop/start; do not ask again for actions already explicitly approved for it. A request to prepare a deployment or implement a change alone is insufficient.
- Once deployment is approved, run only `deploy-firestick.ps1` and report its result. Do not add flags unless requested or perform extra device commands, tests, preference changes or remote/UI actions. The script's build/connect/install/force-stop/start steps are the retained workflow.
- Ask for explicit approval before any other agent-initiated app start, including testing or investigation through ADB, UI/remote automation or helpers. The user may be using the Firestick and must not be interrupted.
- The user withdrew DP-1's stop/start removal before implementation and explicitly chose to retain both actions. Do not remove them under the former approval; retain OPS-001 and follow the revised permission policy in OPS-007.

## Commit and push requests

- When the user asks to commit and push, treat it as a simple request to stage the files, commit with a concise appropriate message and push. Default to all current non-ignored changes, equivalent to `git add .`, unless the user specifies a narrower scope.
- Do not run tests, builds, reviews, extended analysis, requirement updates or other tasks as part of that request. Do not first prepare a staging plan. Earlier implementation verification requirements do not add work to a commit-and-push-only request.
- If what to stage is unclear, or a known issue needs resolution first (including sensitive files or a Git failure), briefly flag it and ask the user before proceeding. Do not investigate or fix it as an extra task without direction.
- Aim to finish within one minute under normal conditions, subject to Git/network operation time. Report the commit and push result briefly.

## Build and verification

- Use the repository Gradle wrapper (8.2) and JDK 17. AGP is 8.2.2, compile/target SDK 34, Build Tools 34.0.0. Java/Kotlin output targets 1.8.
- Compose BOM is 2024.02.02; the lint runtime override is 8.3.2 (artifact 31.3.2). Keep the aligned lint runtime unless a verified tooling change replaces it; do not suppress skipped Compose checks.
- CR-001–CR-035 remediation was explicitly authorized under FIX-001–FIX-010. See docs/reviews/CODE_REVIEW_FIXES.md for implementation and outstanding acceptance; unrelated historical Back/channel-direction/Stop decisions remain pending.
- Do not use Android Studio's bundled Java 21 to run this Gradle version. Set the Gradle JDK explicitly.
- Windows checks: .\gradlew.bat :app:assembleDebug, .\gradlew.bat :app:testDebugUnitTest and .\gradlew.bat :app:lintDebug as relevant to the change.
- After every implementation, run .\test-smoke.ps1 (or -Lint when relevant). It executes fresh local tests and shared fixtures, prints passed/total plus failures/skips/errors, and updates the generated companion test rows in docs/requirements/REQUIREMENTS.md and docs/requirements/TEST_CASES.md. Never substitute old XML or a cached success for a fresh run. Normal compilation stays incremental; no Firestick/provider access is part of this suite.
- When a requirement definition or implementation status changes, review its assertions and scripts/test-catalog.json entry before implementation proceeds: add/modify/remove cases as appropriate, retain stable IDs, and remove references to retired cases. Definitions/status fingerprints must match. After updating actual tests and mappings, use scripts/Update-TestMapping.ps1 -RequirementId <IDs> -ReviewNote <specific reason> to record the review. Never acknowledge drift merely to make validation green; changed criteria must have meaningful assertions or an explicit manual/coverage gap.
- Every requirement, including Open/Partial/Superseded entries, needs a coverage disposition and test row. Passed/Failed are execution outcomes, not product approval or complete requirement acceptance. Preserve Not run/Skipped/Stale/Error distinctions. Do not hand-edit generated test rows, docs/requirements/TEST_CASES.md or pass counts; edit real tests/mappings and rerun the script.
- Prefer high useful regression coverage within the documented 90-second warm-run maintenance target. Do not target 100% coverage, add trivial getter/source-mirroring assertions, invoke real providers, or include slow decoder/device tests by default. Record exclusions honestly. Lint/build/release results and duplicate release executions remain separate from unique testcase totals.
- Prefer .\deploy-firestick.ps1 for the full build/install/launch workflow. Use -BuildOnly -Check for local checks without changing the device; -InstallOnly deliberately uses the existing APK.
- For prose-only documentation edits, check paths and commands; additional application tests are unnecessary. Requirement definition/status or testcase-mapping changes are different: update/review the mappings and run the smoke command to regenerate current results.
- Report actual outcomes. A built APK is not evidence of installation; an activity start is not evidence of usable playback; an old APK is not a verified current build.
- Record deployment verification and unresolved build/test failures in docs/operations/FIRESTICK_DEPLOYMENT.md.

## Architecture and behavior

- Single app module; installed application ID com.inakara.aypitivi, Kotlin namespace com.example.iptv. Exported launcher component: com.inakara.aypitivi/com.example.iptv.SplashActivity. MainActivity is internal. OPS-006 identity/data migration was explicitly approved on 22 October 2026; normal updates target the new ID and must not uninstall it or repeat the legacy migration.
- UI: Compose for TV; ViewModels: Hilt and StateFlow; repository: OkHttp; playback: Media3 ExoPlayer; logos: Coil; preferences: SharedPreferences/Gson.
- UI-004 / DATA-003 / LOGIN-007 were explicitly approved on 22 October 2026. Logo investigation/tests must use local fixtures; the user declined external channel-logo probes. Keep the classic launcher branding separate from the neutral channel fallback and retain the matching Coil SVG decoder. Native Windows image tests use Robolectric 4.12.2.
- BR-1 (UI-007–UI-009, BROWSE-009–BROWSE-012, SET-006, DATA-005) was explicitly approved on 22 October 2026. Preserve the two-level browser, reference sidebar (extended by LV-1 below), whole-control amber focus, Menu favorites, minimal name search and saved density. Playback stops on leaving the player; do not add programme-data features, generated channel numbers or a browsing PLAYING badge. See docs/design/BROWSER_DESIGN_REVIEW.md; publication/deployment was not part of this approval.
- LV-1 (UI-012 / BROWSE-013) was explicitly approved with implement on 23 October 2026. Keep Live TV (screen/default and fresh initial focus), VOD (film), Favorites and Search in that order, plus bottom Settings. Use the existing isVod classification for section-filtered provider categories/counts/recents, shared canonical channel/favorite identities and global Favorites/Search. Preserve separate section/category bookmarks and matching Back/playback/auxiliary returns. See docs/design/LIVE_VOD_BROWSER_REVIEW.md; no deployment, provider API migration or audio-decoder addition is authorized by this scope.
- CF-1 (UI-013 / SET-008) was explicitly approved with approved, implement on 23 October 2026. Headings identify the current opened category/destination. Preserve the device-wide saved adult filter, active by default, using case-insensitive category-name substring matching across sections, recents, Favorites, Search and browser playback collections. Keep full snapshots/IDs/favorites/history and safe Settings/section returns. See docs/design/CATEGORY_FILTER_REVIEW.md; deployment remains separate.
- SC-1 (LOGIN-002/003/008, DATA-006/007, SET-006/007, PLAY-007) was explicitly approved for implementation. Preserve account-scoped no-backup snapshots, saved-list-first startup, a refresh after first layout when over 24 hours old, manual/account controls in Settings, failure retention and foreground playback-only keep-screen-on. Deployment still requires separate consultation under the latest instruction above.
- MD-1 (DATA-008, LIB-002, VOD-006, BROWSE-015, PLAY-014 and the selected BROWSE-013 VOD-root/return extension) was explicitly approved on 24 October 2026. Preserve the existing Live TV/M3U startup and full legacy VOD fallback, built-in SQLite paging/on-demand metadata, typed identities with exact additive legacy aliases, independent durable progress/favorite references, reviewed watching defaults, remote nested returns and the existing category-name adult filter. See docs/design/METADATA_LIBRARY_REVIEW.md. EPG, stronger adult flags, startup optimization, external/local/network sources and deployment remain separate.
- MD-2 (REL-005, PLAY-015, UI-015, VOD-007, LIB-003) was explicitly approved on 24 October 2026. Defer optional metadata reconciliation until a relevant destination after first layout; retain old identities/favorites/progress. Use equal density-aware VOD root tiles with root-only Playlist categories, saved HH:MM Resume labels and one actual-state watched action. Remove routine library Back/Refresh, keep remote Back/automatic freshness and offer specific failure-only retries. Info shares three-second inactivity expiry with controls and closes on forced hide without reopening them. This replaces MD-1's indefinite Info suspension and repeated fallback/control presentation. See docs/design/METADATA_FOLLOWUP_REVIEW.md. Device timing/deployment remain separately approved operations.
- Startup diagnostics are debug-only and accept fixed phase/outcome names and numeric durations/counts. Never add URLs, account/channel names or raw exceptions. Download and parsing overlap in a single streaming span; measure total Connect-to-first-browse-layout time instead of adding overlapping phases or inferring performance from loading labels.
- Keep network and parsing work off the main thread; release the player when leaving playback.
- Remote focus is functionality. Check empty categories, offscreen lazy-list items, sidebar/grid transitions and return from playback when touching browsing.
- Do not claim HLS/DASH/RTMP support without checking player modules and testing an appropriate stream.
- Use the parser's InputStream API in tests. Use local synthetic playlists, without contacting a real provider.

## Documentation

- Keep repository Markdown under docs/ except root README.md and AGENTS.md: requirements/generated cases in docs/requirements/, design reviews in docs/design/, investigations in docs/investigations/, code reviews in docs/reviews/ and deployment guidance in docs/operations/. docs/backlog.md tracks unresolved decisions and future requests, not implementation approval. Maintain relative links, catalog source references and smoke/mapping script paths; commands run from repository root.
- Keep README.md grounded in implemented behavior.
- Keep docs/requirements/REQUIREMENTS.md current through the review workflow above; keep personal settings and historical private data out of the catalog.
- Put repeatable Windows/Android Studio/Fire TV steps in docs/operations/FIRESTICK_DEPLOYMENT.md with expected results and troubleshooting.
- Distinguish confirmed results from untested guidance. Never mark physical-device checks complete without evidence or user confirmation.
