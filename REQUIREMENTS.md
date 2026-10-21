# Aypi Tivi requirement catalog

**Initial retrospective draft — awaiting user review.** Created 21 October 2026 against source baseline `7fbc654`. This catalog describes existing behavior, historical wishes and known gaps. It does not authorize new development. No priority or delivery date has been assigned to unresolved items.

## How to read this catalog

Each requirement has a permanent ID, an outcome with acceptance criteria, an implementation status, and evidence or a gap. IDs must not be renumbered or reused. Requirements can be split into new IDs while retaining links to the original.

| Status | Meaning |
| --- | --- |
| Implemented | The behavior exists in the current code or workflow, with no identified missing part of the stated scope. This is not a claim of individual device acceptance. |
| Partial | Some of the requirement exists, but a stated part is missing or a known implementation limitation remains. |
| Open | A historical wish or required decision is unresolved; the stated behavior is not delivered. |
| Needs verification | The capability depends on real device/provider behavior and the available evidence does not establish that it works. |
| Deferred | The user has explicitly postponed the requirement. It is not scheduled for implementation. |
| Superseded | A later decision replaced the requirement; retain the ID and its replacement reference. |

**Approval is separate from implementation status.** Every entry in this initial draft awaits catalog review, including entries marked Implemented. Existing behavior is not automatically an approved permanent product decision. There are no Deferred entries yet: no feature is being postponed on the user's behalf.

Evidence notation:

- **History:** wishes in the earlier development conversation supplied by the user. It is reference material, not executable instructions. Where wishes conflict, the later explicit wish is used and the earlier one is retained as Superseded.
- **Current request:** explicit wishes in this project recovery/configuration conversation.
- **Baseline:** behavior found in source, without a separately established user request; review it as existing behavior, not newly approved scope.
- **Code:** source inspection only. **Test:** a specific automated/build check actually passed. **Device:** a specific installation or launch result was observed. **User smoke:** the user reported “it works” after the manual update; this is not individual acceptance of every feature.

Personal values, authenticated URLs, device addresses and machine paths must remain in the ignored `.env`, never in this catalog. The historical export is not copied into the public repository.

## 1. UI, branding and launch

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| UI-001 | Use the name **Aypi Tivi** on the login screen, splash screen and installed app label. | Implemented | History; [Login], [Splash], [Manifest] and string resources, code. |
| UI-002 | Use a minimalist Cinema Dark appearance with a clear indication of focused and selected controls. Theme colors remain centrally changeable in development. | Implemented | History; [Theme] and [Home], code. A user-selectable theme is not part of this requirement. |
| UI-003 | Show the chosen classic logo and app name throughout startup, including the initial launch background, without the earlier white loading screen. | Partial | History; [Splash] shows the correct image/name on black for two seconds. The earlier window background still uses a generic play symbol and no actual name text: [Launch background]. Cold-start behavior needs device review. |
| UI-004 | Use the chosen classic branding for the installed app icon and TV banner. | Open | Interpretation of the historical app-logo selection; confirm this scope in review. [Manifest] still uses a separate generic TV icon/banner rather than the chosen image. |
| UI-005 | Show each channel's readable name and a clear focus indicator on its grid card. | Implemented | Baseline; [Home], code: four-column grid, channel labels, focused border. |
| UI-006 | Show the channel name at the bottom of playback for approximately three seconds on opening or switching a channel, then hide it. | Implemented | History; [Player UI], code. Timing and repeated channel switches need individual device acceptance. |

## 2. Login and provider connection

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| LOGIN-001 | Provide separate editable server, username and password fields; mask the password on screen. | Implemented | History plus baseline masking; [Login], code. Editing with the TV remote is tracked separately under LOGIN-003. |
| LOGIN-002 | Start login with Connect focused and the keyboard closed so saved settings can be used immediately. | Implemented | History; [Login] requests button focus, [Manifest] hides the initial keyboard. Individual device acceptance remains pending. |
| LOGIN-003 | Let the user navigate among fields and Connect without opening the keyboard; Select/Enter on a field opens editing and allows text changes. | Partial | History and current inability-to-edit report; [Login] now explicitly requests the keyboard on Select/Enter. Text fields do not explicitly suppress keyboard-on-focus behavior; the entire navigation/edit cycle has not been individually verified after the fix. |
| LOGIN-004 | Download the provider's `get.php` playlist using the supplied account and `type=m3u_plus`; accept a base address with or without scheme or a final slash. | Implemented | History/current request; [Repository], code. This is an M3U endpoint, not a full Xtream API integration. General app operation has User smoke evidence. |
| LOGIN-005 | Reject missing fields, indicate loading while connecting, and show a useful error with a retry path for failed, empty or invalid playlist responses. | Implemented | Baseline/recovery expectations; [Login state] and [Login], code. Error visibility on the TV and all provider failure cases have not been individually accepted. |
| LOGIN-006 | Save successful login fields and prefill them on later launches; do not replace saved credentials merely because build defaults change. | Implemented | History/current request; [Login state] and [Credentials], code. The user observed saved fields in the previously installed app. Saving is triggered by a nonempty playlist result. |

## 3. Playlist parsing and channel logos

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| DATA-001 | Read extended M3U entries into channel names, stream URLs, group titles, logo URLs, TVG identifiers/names and user-agent metadata. Ignore comments and blank lines between entries. | Implemented | History; [Parser], code. Test: one representative two-channel fixture passes. Exhaustive M3U dialect compatibility is not established. |
| DATA-002 | Assign usable defaults when optional name/category metadata is absent, allowing otherwise usable entries to be browsed. | Implemented | Baseline; [Parser], code: Unknown Channel and Uncategorized defaults. |
| DATA-003 | Display reachable channel logos from `tvg-logo`, using cached images on repeat loads and a placeholder when no logo URL exists. | Needs verification | Repeated historical logo failures; [Home] and [Image loader] implement loading/cache and a missing-URL placeholder. No provider-logo acceptance test is recorded; failed-image loading does not have the same explicit placeholder path. |
| DATA-004 | Honor a playlist entry's required user-agent when requesting its stream. | Open | Compatibility interpretation of the supplied historical playlist metadata; review whether required providers need this. [Parser] stores it, but [Player state] does not apply it. |

## 4. Categories, channel browsing and navigation

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| BROWSE-001 | Group channels by playlist category and display the active category's channels in a grid. Selecting a card starts that channel. | Implemented | History/baseline; [Home state], [Home] and [Navigation], code; User smoke covers general operation only. |
| BROWSE-002 | Keep the category sidebar visible and opaque beside the channel grid so content does not obscure category labels. | Implemented | Later historical decision; [Home], code. Replaces OLD-001. |
| BROWSE-003 | Put Recently viewed first, then categories beginning with `TR` regardless of case, then the remaining categories alphabetically. | Implemented | History; [Home state], code. Sorting within the TR group is also alphabetical in the baseline. |
| BROWSE-004 | Right from the sidebar moves focus to the top-left channel of the active category, including after the grid was scrolled. If the category has no channels, keep usable focus without crashing. | Partial | History; [Home] requests focus on item zero but does not scroll it into composition or guard an empty category. |
| BROWSE-005 | Left from a channel in the leftmost grid column returns focus to the currently active category; Left elsewhere moves within the grid. Returning must work even if the active category is offscreen. | Partial | History; [Home] checks the four-column boundary and requests active-category focus, but does not ensure that lazy sidebar item is composed. |
| BROWSE-006 | Back from the channel grid returns to category selection before offering to exit the app. | Open | Explicit historical wish; [Home] has no context-sensitive Back handler. The current browsing Back handler is the exit prompt in [Navigation]. |
| BROWSE-007 | Back from playback returns to the category from which playback started, rather than resetting to the initial category or leaving the app. | Implemented | History; [Navigation] clears the playing selection and [Home state] retains the category by name. Exact grid position/focus restoration is not claimed. |
| BROWSE-008 | At the browsing top level, Back asks for confirmation before exiting; Cancel stays in the app and Exit closes it. | Implemented | History; [Navigation], code. BROWSE-006 defines the missing distinction between grid and top-level Back. |

## 5. Recently viewed

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| RECENT-001 | Always show Recently viewed at the top of the category list, including when no channel has yet been watched. | Implemented | History; [Home state], code. Empty-category navigation remains covered by BROWSE-004. |
| RECENT-002 | Keep up to 20 unique recently selected channels, newest first; revisiting a channel moves it to the front rather than duplicating it. | Implemented | Baseline detail of the historical recently-viewed feature; [Recent storage] and [Navigation], code. Identity is the stream URL; selection starts history tracking, not proof of successful playback. |
| RECENT-003 | Persist recent channels across app launches and refresh the list when returning from playback. | Implemented | History/baseline; [Recent storage], [Home] and [Home state], code. Separate relaunch acceptance is pending. |

## 6. Live playback and remote controls

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| PLAY-001 | Use ExoPlayer as the core player and play supported provider streams with picture and sound on the Fire TV Stick 4K Max. | Implemented | Explicit history; [Player state] configures Media3 ExoPlayer and audio. Historical and current User smoke reports establish general operation, not every codec or channel. |
| PLAY-002 | Switch channels with Up/Down while remaining inside the active category, wrapping at its ends. | Implemented | History/baseline wrapping; [Player UI] and [Navigation], code. Direction semantics remain unresolved in PLAY-003. |
| PLAY-003 | Down selects the next channel in the current category. Review the corresponding Up mapping before implementation. | Open | Later explicit historical direction request. Current code maps Up to next and Down to previous. Whether dedicated Channel Up/Down should follow the same ordering also needs review. |
| PLAY-004 | Select or Play/Pause toggles playback; dedicated Play and Pause buttons perform their named actions. | Implemented | History/baseline; [Player UI], code. Separate physical-remote acceptance is pending. |
| PLAY-005 | Release playback resources when leaving the player and allow another channel to be opened afterward. | Implemented | Baseline reliability behavior; [Player UI] and [Player state], code. Background/resume behavior is not covered by this narrow requirement. |
| PLAY-006 | Play a compatible 4K stream at its native resolution when the device, decoder, display and network support it. | Needs verification | Historical question/capability expectation; [Player state] imposes no explicit resolution cap, but no 4K decoder/output test is recorded. No blanket codec, HDR or performance guarantee is established. |

## 7. Video on demand

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| VOD-001 | Recognize likely VOD entries so live and on-demand content can receive appropriate controls. | Implemented | History; [Parser], code. Current detection is heuristic using category words, URL paths and file extensions, not provider-guaranteed classification. |
| VOD-002 | Pause and resume a VOD item with the remote without leaving playback. | Implemented | History; [Player UI], code. Individual seekable-VOD device acceptance is pending. |
| VOD-003 | Move backward/forward in a seekable VOD item; current increments are 30 seconds on Left/Right and 10 seconds on dedicated Rewind/Fast Forward. | Partial | History plus baseline increments; [Player UI] and [Player state]. D-pad seeks clamp to a known duration; dedicated Fast Forward does not guard an unknown duration, and seeking is not consistently gated by stream seekability. Review increments; no universal live-stream seeking is claimed. |
| VOD-004 | Provide the requested Stop operation for on-demand playback, distinct from Pause. | Open | Historical wording requested stop, play and position control. No dedicated Stop action is handled. Review whether Stop means exit playback, reset position, or another behavior. |
| VOD-005 | Show brief feedback after a seek request, and allow repeated identical seek actions to refresh that feedback. | Partial | Baseline feedback behavior, not a separately established historical wish; [Player UI] shows a three-second seek message. Repeated identical text does not restart the current state-keyed timer. Review whether to retain this acceptance criterion. |

## 8. Settings and local configuration

There is currently no dedicated in-app settings screen. Editable connection fields and saved preferences are the runtime settings; `.env` is configuration for local builds and deployment. A new settings menu is not implicitly authorized by this catalog.

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| SET-001 | Read local debug defaults for provider URL, username and password from an ignored `.env` at build time; do not hardcode personal values in source. | Implemented | Current request; [Build] and [Credentials]. Test: generated debug fields match the private file. This is not runtime reading of the computer's `.env`. |
| SET-002 | Supply a public `.env.example` with placeholders for provider fields, retired addresses, Firestick destination and optional JDK/SDK paths. | Implemented | Current request; [.env.example]. Real values stay local. |
| SET-003 | Build successfully without `.env`; release builds have empty login defaults even when a private `.env` is present. | Implemented | Current publication requirement/baseline privacy measure; [Build]. Test: no-.env debug build passed; release generated fields were checked empty. |
| SET-004 | Optionally replace a saved retired server address using the configured replacement, while preserving other custom servers and saved account fields. | Implemented | Current provider-change recovery; [Credentials]. Retired-address list and replacement are private configuration. |
| SET-005 | Treat `.env` entries as literal key/value data, including quoted values and values containing `=` or `#`; reject malformed entries or duplicate keys without executing their contents. | Implemented | Baseline safety behavior; [Build] and [Deploy script]. Tests covered literal parsing, malformed entries and duplicate keys in PowerShell. Cross-reader edge cases are not exhaustively tested. |

## 9. Reliability, privacy and compatibility

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| REL-001 | Keep network downloads and playlist/category processing off the UI thread, and use lazy channel/category lists to avoid unnecessary work. | Implemented | Historical request for simpler/faster code; [Repository], [Home state] and [Home], code. This records concrete baseline measures, not a measured speed guarantee. |
| REL-002 | Show connection/playback errors and provide a way back instead of silently abandoning the user. Attempt to display unexpected crash details for diagnosis. | Partial | History/recovery; [Login], [Player UI], [Crash handler] and [Crash UI]. Error UI exists; the crash handler starts a screen then kills its process, so reliable crash-screen delivery is not established. |
| REL-003 | Run on the intended Fire TV hardware using its remote, without requiring a touchscreen. | Implemented | History; [Manifest], code; Device: Android 9/API 28 installation and launch succeeded. The complete remote matrix is still subject to individual checks above. |
| PRIV-001 | Keep real credentials, provider/device addresses and personal machine paths out of public source, examples, documentation and published history. Exclude private configuration, builds, caches, logs and signing material. | Implemented | Current request; [.gitignore], source/docs cleanup and clean initial publication. Test: the published tree was checked against known private values; old history was not pushed. |
| PRIV-002 | Retain saved app data during a normal update; do not uninstall or clear app data as an automatic deployment recovery action. | Implemented | Current deployment workflow; [Deploy script] uses install -r. Device: update and launch succeeded; individual stored-data checks were not separately reported. |
| PRIV-003 | Keep personal defaults out of distributable release defaults, and document that local debug APKs contain their embedded configuration. | Implemented | Current public-repository objective; [Build] and [Deployment guide]. Generated release defaults were checked; debug APKs remain private local outputs. |

Saved device credentials currently use unencrypted app-private SharedPreferences, the manifest allows provider HTTP traffic, and diagnostic exceptions can contain account URLs. These are disclosed baseline limitations. Encryption, backup policy changes and transport restrictions have not been approved as new requirements in this initial catalog.

## 10. Build, deployment and project documentation

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| OPS-001 | Provide one PowerShell command that builds the current source, connects to the configured Firestick, installs an update and launches the app. | Implemented | Current request; [Deploy script]. Device: the full script run built, installed and received successful activity launch. |
| OPS-002 | Stop deployment after build/check, connection or installation failure; address only the explicitly configured device. | Implemented | Current request/baseline safeguards; [Deploy script]. Tests covered native-command failure handling; all possible ADB failure modes were not individually exercised. |
| OPS-003 | Support build-only, install-only, and optional unit-test/lint modes, with clear distinctions between building code and installing an existing APK. | Implemented | Baseline script options and current workflow explanation; [Deploy script] and [Deployment guide]. Build-only with checks and the normal full run passed; install-only is code-inspected. |
| OPS-004 | Keep a reproducible Gradle wrapper and documented compatible build setup; explain everyday deployment separately from one-time setup and troubleshooting. | Implemented | Current request; wrapper, [README] and [Deployment guide]. Build/tests/lint passed using the documented pinned setup; no library migration was performed. |
| OPS-005 | Publish the cleaned app source and documentation to the user-selected GitHub repository without uploading private settings or earlier secret-bearing history. | Implemented | Current request; baseline `7fbc654` was pushed as a single clean initial main commit. This row records that completed publication, not automatic permission to publish future changes. |

## 11. Development and review process

| ID | Requirement and acceptance criteria | Status | Origin, evidence and gaps |
| --- | --- | --- | --- |
| DEV-001 | For every new wish or change to behavior, first add or revise requirements, acceptance criteria, status and relevant dependencies; present the catalog changes for user review before implementation. | Implemented | Current explicit request; documented below and in [README]/[Agent guidance]. Future compliance is procedural, not an app feature. |
| DEV-002 | Implement only the requirement IDs and scope the user has explicitly approved. Update their status and validation evidence as work proceeds, including gaps that remain. | Implemented | Current request; documented workflow. Initial catalog review does not by itself select every Open item for development. |
| DEV-003 | Explain material technology/architecture choices and their tradeoffs in plain language as part of the requirement proposal, before implementing the selected approach. | Implemented | Historical decision preference reinforced by the new review workflow; [Agent guidance]. No new architecture is selected by this catalog. |

## 12. Replaced historical requirements

| ID | Earlier requirement | Status | Replacement |
| --- | --- | --- | --- |
| OLD-001 | Automatically hide the category sidebar after moving to channels. | Superseded | The later historical instruction explicitly keeps it visible: BROWSE-002. |
| OLD-002 | Store personal provider/account defaults directly in application source. | Superseded | Private build-time configuration and public placeholders: SET-001 and SET-002. No historical personal values are retained here. |

## Decisions for the initial review

These are review points, not an implementation queue. The user may correct, approve, remove, split or explicitly defer entries.

1. **Branding:** confirm whether UI-003 covers the earliest launch background as well as the custom splash, and whether UI-004 should replace the installed app icon/banner with the classic logo.
2. **Login editing:** confirm the full keyboard behavior in LOGIN-003 on the Firestick; the latest specific fix has not had an individually reported editing test.
3. **Browsing:** confirm BROWSE-004/005 focus behavior and BROWSE-006 Back hierarchy, including empty and long/scrolled categories.
4. **Channel order:** confirm Down = next in PLAY-003 and specify Up plus the dedicated Channel Up/Down buttons. This would change the current mapping.
5. **VOD:** define Stop in VOD-004, confirm seek increments in VOD-003 and whether repeatable seek feedback in VOD-005 belongs in scope.
6. **Provider compatibility:** decide whether DATA-004 is needed; separately verify logos (DATA-003) and 4K (PLAY-006) using suitable content.
7. **Baseline scope:** review entries marked Baseline or Interpretation. They record existing behavior or a disclosed inference and are not evidence of an earlier feature approval. Select priorities only after this review.

No unrequested feature roadmap has been added. Deferred is available for the user's explicit decisions; it is not used to label missing features that were never requested.

## Workflow from this point onward

1. **Capture the wish.** Inspect relevant evidence and edit this catalog first. Add a permanent ID or identify affected existing IDs. State the desired outcome, testable acceptance criteria, proposed status, dependencies and unresolved decisions. Do not start implementation in the same step.
2. **Present a concrete review.** Summarize added/changed IDs and changed behavior, distinguishing user requests from interpretations. Give the user the catalog/diff to review. Do not silently broaden acceptance criteria after approval.
3. **Wait for explicit approval.** Catalog drafting, read-only investigation and clarification may continue, but app changes, implementation tests, build/deployment changes and deployment for that scope must wait. A generic wish, silence, an old approval or “implemented” baseline status is not approval of a new catalog revision. An explicit approval can cover named IDs or a clearly identified revision and implementation scope.
4. **Implement the selected scope.** Approval of the initial retrospective catalog establishes a baseline; it does not automatically authorize development of all unresolved entries. Proceed once the user selects/approves implementation scope. If a new product decision or scope change is discovered, update the catalog and obtain review of that change before implementing it.
5. **Record the outcome.** Reference requirement IDs in the implementation summary and in commits/PRs when created. Update statuses and evidence in this catalog, separate code/build/test/device results, and retain unresolved gaps. Mark Deferred or Superseded only with the user's decision and the replacement/reason.

Routine requirement-document edits and investigation needed to prepare a review are permitted. Do not create app changes, commits, pushes or deployments merely to complete this initial review artifact.

### Review/change record

| Date | Revision / scope | Review state | Result |
| --- | --- | --- | --- |
| 21 October 2026 | Initial retrospective catalog against `7fbc654` | Awaiting user review | Documentation only; no new implementation authorized. |

For later revisions, add the affected IDs, a brief change summary, the user's approval decision, and the revision/commit reference when one exists. Do not fabricate approval dates or infer approval from a lack of response.

Evidence links above point to the source files inspected for this draft.

[README]: README.md
[Agent guidance]: AGENTS.md
[Deployment guide]: FIRESTICK_DEPLOYMENT.md
[Deploy script]: deploy-firestick.ps1
[.env.example]: .env.example
[.gitignore]: .gitignore
[Build]: app/build.gradle.kts
[Manifest]: app/src/main/AndroidManifest.xml
[Navigation]: app/src/main/java/com/example/iptv/MainActivity.kt
[Login]: app/src/main/java/com/example/iptv/ui/login/LoginScreen.kt
[Login state]: app/src/main/java/com/example/iptv/ui/login/LoginViewModel.kt
[Credentials]: app/src/main/java/com/example/iptv/data/storage/CredentialsManager.kt
[Repository]: app/src/main/java/com/example/iptv/data/repository/ChannelRepositoryImpl.kt
[Parser]: app/src/main/java/com/example/iptv/data/parser/M3UParser.kt
[Home]: app/src/main/java/com/example/iptv/ui/home/HomeScreen.kt
[Home state]: app/src/main/java/com/example/iptv/ui/home/HomeViewModel.kt
[Recent storage]: app/src/main/java/com/example/iptv/data/storage/RecentlyWatchedManager.kt
[Player UI]: app/src/main/java/com/example/iptv/ui/player/PlayerScreen.kt
[Player state]: app/src/main/java/com/example/iptv/ui/player/PlayerViewModel.kt
[Theme]: app/src/main/java/com/example/iptv/ui/theme/Theme.kt
[Splash]: app/src/main/java/com/example/iptv/SplashActivity.kt
[Launch background]: app/src/main/res/drawable/splash_background.xml
[Image loader]: app/src/main/java/com/example/iptv/di/AppModule.kt
[Crash handler]: app/src/main/java/com/example/iptv/util/GlobalExceptionHandler.kt
[Crash UI]: app/src/main/java/com/example/iptv/ui/error/ErrorActivity.kt
