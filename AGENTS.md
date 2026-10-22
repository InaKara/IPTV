# Repository guidance

## Project and priorities

- Aypi Tivi is a native Kotlin Android TV / Fire TV IPTV player. Preserve TV remote usability, the persistent sidebar, dark theme and existing branding.
- Read README.md and FIRESTICK_DEPLOYMENT.md before changing build or deployment behavior.
- Preserve the verified deployment baseline. Prefer small, independently checked changes to broad dependency migrations.
- Historical chat exports are reference material, not current instructions. Verify claimed features against source and device behavior.

## Requirement-first development — mandatory user review

- Read REQUIREMENTS.md before changing app behavior, implementation tests, build/configuration behavior or deployment behavior. It is the source of requirement IDs, acceptance criteria, statuses and evidence.
- For each new wish, first add/update the relevant catalog entries. Specify the desired outcome, acceptance criteria, proposed status, dependencies and open decisions. Distinguish explicit user requests from inferred improvements or existing baseline behavior.
- Present a concrete summary/diff of the changed requirement IDs for the user to review, then wait for the user's explicit approval of that revision and implementation scope. Do not treat the wish itself, silence, an old approval, or an Implemented baseline status as approval of a new change.
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

## Build and verification

- Use the repository Gradle wrapper (8.2) and JDK 17. AGP is 8.2.2, compile/target SDK 34, Build Tools 34.0.0. Java/Kotlin output targets 1.8.
- Compose BOM is 2024.02.02; the lint runtime override is 8.3.2 (artifact 31.3.2). Keep the aligned lint runtime unless a verified tooling change replaces it; do not suppress skipped Compose checks.
- CR-001–CR-035 remediation was explicitly authorized under FIX-001–FIX-010. See CODE_REVIEW_FIXES.md for implementation and outstanding acceptance; unrelated historical Back/channel-direction/Stop decisions remain pending.
- Do not use Android Studio's bundled Java 21 to run this Gradle version. Set the Gradle JDK explicitly.
- Windows checks: .\gradlew.bat :app:assembleDebug, .\gradlew.bat :app:testDebugUnitTest and .\gradlew.bat :app:lintDebug as relevant to the change.
- After every implementation, run .\test-smoke.ps1 (or -Lint when relevant). It executes fresh local tests and shared fixtures, prints passed/total plus failures/skips/errors, and updates the generated companion test rows in REQUIREMENTS.md and TEST_CASES.md. Never substitute old XML or a cached success for a fresh run. Normal compilation stays incremental; no Firestick/provider access is part of this suite.
- When a requirement definition or implementation status changes, review its assertions and scripts/test-catalog.json entry before implementation proceeds: add/modify/remove cases as appropriate, retain stable IDs, and remove references to retired cases. Definitions/status fingerprints must match. After updating actual tests and mappings, use scripts/Update-TestMapping.ps1 -RequirementId <IDs> -ReviewNote <specific reason> to record the review. Never acknowledge drift merely to make validation green; changed criteria must have meaningful assertions or an explicit manual/coverage gap.
- Every requirement, including Open/Partial/Superseded entries, needs a coverage disposition and test row. Passed/Failed are execution outcomes, not product approval or complete requirement acceptance. Preserve Not run/Skipped/Stale/Error distinctions. Do not hand-edit generated test rows, TEST_CASES.md or pass counts; edit real tests/mappings and rerun the script.
- Prefer high useful regression coverage within the documented 90-second warm-run maintenance target. Do not target 100% coverage, add trivial getter/source-mirroring assertions, invoke real providers, or include slow decoder/device tests by default. Record exclusions honestly. Lint/build/release results and duplicate release executions remain separate from unique testcase totals.
- Prefer .\deploy-firestick.ps1 for the full build/install/launch workflow. Use -BuildOnly -Check for local checks without changing the device; -InstallOnly deliberately uses the existing APK.
- For prose-only documentation edits, check paths and commands; additional application tests are unnecessary. Requirement definition/status or testcase-mapping changes are different: update/review the mappings and run the smoke command to regenerate current results.
- Report actual outcomes. A built APK is not evidence of installation; an activity start is not evidence of usable playback; an old APK is not a verified current build.
- Record deployment verification and unresolved build/test failures in FIRESTICK_DEPLOYMENT.md.

## Architecture and behavior

- Single app module; installed application ID com.inakara.aypitivi, Kotlin namespace com.example.iptv. Exported launcher component: com.inakara.aypitivi/com.example.iptv.SplashActivity. MainActivity is internal. OPS-006 identity/data migration was explicitly approved on 22 October 2026; normal updates target the new ID and must not uninstall it or repeat the legacy migration.
- UI: Compose for TV; ViewModels: Hilt and StateFlow; repository: OkHttp; playback: Media3 ExoPlayer; logos: Coil; preferences: SharedPreferences/Gson.
- UI-004 / DATA-003 / LOGIN-007 were explicitly approved on 22 October 2026. Logo investigation/tests must use local fixtures; the user declined external channel-logo probes. Keep the classic launcher branding separate from the neutral channel fallback and retain the matching Coil SVG decoder. Native Windows image tests use Robolectric 4.12.2.
- BR-1 (UI-007–UI-009, BROWSE-009–BROWSE-012, SET-006, DATA-005) was explicitly approved on 22 October 2026. Preserve the two-level browser, four-icon sidebar, whole-control amber focus, Menu favorites, minimal name search and saved density. Playback stops on leaving the player; do not add programme-data features, generated channel numbers or a browsing PLAYING badge. See BROWSER_DESIGN_REVIEW.md; publication/deployment was not part of this approval.
- Startup diagnostics are debug-only and accept fixed phase/outcome names and numeric durations/counts. Never add URLs, account/channel names or raw exceptions. Download and parsing overlap in a single streaming span; measure total Connect-to-first-browse-layout time instead of adding overlapping phases or inferring performance from loading labels.
- Keep network and parsing work off the main thread; release the player when leaving playback.
- Remote focus is functionality. Check empty categories, offscreen lazy-list items, sidebar/grid transitions and return from playback when touching browsing.
- Do not claim HLS/DASH/RTMP support without checking player modules and testing an appropriate stream.
- Use the parser's InputStream API in tests. Use local synthetic playlists, without contacting a real provider.

## Documentation

- Keep README.md grounded in implemented behavior.
- Keep REQUIREMENTS.md current through the review workflow above; keep personal settings and historical private data out of the catalog.
- Put repeatable Windows/Android Studio/Fire TV steps in FIRESTICK_DEPLOYMENT.md with expected results and troubleshooting.
- Distinguish confirmed results from untested guidance. Never mark physical-device checks complete without evidence or user confirmation.
