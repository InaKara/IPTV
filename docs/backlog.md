# Backlog

Updated 23 October 2026. [REQUIREMENTS.md](requirements/REQUIREMENTS.md) is the source of stable requirement IDs, approval decisions, criteria and test evidence. This list consolidates unresolved decisions, requested features and validation gaps. No priority, delivery date or implementation approval is implied. New feature requests are Open, not Deferred; each needs a concrete scope/design review before implementation.

## Outstanding decisions and known issues

| Item | Current evidence / status | What needs clearing | Trace |
| --- | --- | --- | --- |
| MP2 audio / silent channels | PLAY-008 Partial; PLAY-010 Open. The reported stream has MP2 stereo 48 kHz. Approved read-only Firestick configuration files declare MP3 but no MP2; runtime decoder/output is unverified. No software decoder is bundled. | Decide whether to approve AU-1: official version-matched Media3/FFmpeg fallback with reproducible native builds. Clarify size, ABIs, tooling, maintenance and third-party obligations; validate synthetic decoding and separately authorized audible playback. | [Audio proposal](design/LATEST_APP_REVIEW.md); PLAY-008/010 |
| Large-playlist memory exhaustion | A historical Android parsing crash exhausted a 192 MiB heap. SF-1 fixed blank-category snapshot rejection and verified real first-load/cache startup; it did not establish heap safety. | Profile representative Android peak/retained memory during initial load, refresh and cached startup, then review a focused correction. Desktop parsing and a larger heap flag are not proof of safety. | [Investigation](investigations/STARTUP_FAILURE_INVESTIGATION.md); REL-001/002, DATA-006, LOGIN-008 |
| Startup/cache performance | One verified cached startup took 14.857 seconds after the splash; this is one sample. | Establish repeatable timings and an acceptable target; identify snapshot/grouping costs before selecting optimizations. | LOGIN-007/008, REL-001; [startup evidence](investigations/STARTUP_FAILURE_INVESTIGATION.md) |
| Channel direction | PLAY-003 Open. Historical request: Down selects next; Up mapping remains unresolved. | Agree on both directions, dedicated channel keys, wrapping and interaction with visible controls. | PLAY-002/003/009/011 |
| VOD Stop | VOD-004 Open; distinct Stop behavior is undefined. | Define Stop versus Pause/Back, position reset/resume and input behavior. Coordinate persistent viewing memory. | VOD-004/006, PLAY-011 |
| Actual 4K playback | PLAY-006 Needs verification. Packaging/player modules do not establish native 4K output. | Verify compatible content, device/display resolution, decoder, sound and sustained playback after separate authorization. | PLAY-006, REL-003 |
| Live TV rewind/fast-forward | User reports pause/resume and speed changes. Current PlayerViewModel explicitly requires isVod for seeking and removes native seek commands for live entries. No actual provider live-window capability was inspected. | Investigate whether selected streams expose a seekable live/DVR window. Pause/speed do not prove rewind support. Forward can only catch up to the live edge; a nonseekable stream would require a separately scoped recording/timeshift approach. No seek policy change is approved. | PLAY-011, PLAY-001; [Media3 live-stream documentation](https://developer.android.com/media/media3/exoplayer/live-streaming) |
| Authoritative Live TV/VOD types | Current classification uses category/path/extension heuristics; structured provider types are unverified. | Decide whether provider API types, mixed/unknown handling and fallback rules warrant a separate proposal. | VOD-001, BROWSE-013; [metadata findings](design/LATEST_APP_REVIEW.md) |
| Unused server metadata / protocol scope | M3U only; retained EPG identifiers have no programme-guide consumer. Provider API/library metadata and actual RTMP need are unverified. | Scope EPG/library/provider discovery separately; confirm required protocols/headers before modules or provider requests. | EPG-001, LIB-001, DATA-001/004, PLAY-001 |
| General retrospective catalog review | Initial catalog review remains pending; selected approvals remain valid. | Review unresolved baseline choices and coverage without reopening settled BR-1/LV-1/CF-1 decisions or approving every Open entry. | [Catalog/change record](requirements/REQUIREMENTS.md); DEV-001–006 |

## Requested features awaiting scope/design review

Explicit requests recorded on 23 October 2026. These proposed outcomes are starting points, not approved detailed designs.

| ID | Feature / desired outcome | Decisions and dependencies |
| --- | --- | --- |
| VOD-006 | **Persistent VOD memory:** watched items, partial progress and continuing where last reached across visits/restarts. | Completion threshold; auto-resume versus prompt; restart/mark watched/unwatched/reset; account/profile scope; episode progression and stable identity after playlist changes. Current background/foreground resume is session-only. Coordinate VOD-004 and LIB-001. |
| EPG-001 | **EPG support:** map programme data to channels and display an agreed guide/now-next view. | XMLTV versus provider API/user sources; sparse IDs, time zones, refresh/cache/retention and any catch-up/reminder scope. No guide requests/UI today. BR-1's previous exclusion remains applicable to its delivered scope. |
| PLAY-011 | **Better playback controls:** improve layout and interaction beyond the existing focus correction. | Desired buttons, timeline/seek, reveal/timeout, audio/subtitle selection, channel information and remote/touch/pointer behavior. PLAY-009 already provides amber native focus/Menu/key routing. Coordinate direction, Stop and live-seek questions. |
| LIB-001 | **Video library support:** structured video browsing/playback. | Provider VOD, local files, network shares or combinations; movie/series metadata, artwork, navigation/search, refresh, permissions and identity. Current VOD is a filtered playlist, not a full library. Coordinate VOD-006. |
| INT-001 | **put.io support:** connect an account, browse and play agreed content. | Authentication/token storage, service/API capability, streaming versus downloads, navigation/metadata and account separation. Integration is absent; no service/account access is authorized by this entry. Coordinate LIB-001/VOD-006. |
| INPUT-001 | **Touch-screen support:** usable touch browsing, Settings and playback. | Tap/swipe/scroll/seek gestures, keyboard behavior, target sizes, orientation and remote-focus coexistence. Optional touchscreen declaration does not prove usability. Coordinate PLAT-001/PLAY-011. |
| PLAT-001 | **Android TV and smartphone installability:** install, launch and update on agreed targets. | Supported Android versions/devices, feature/launcher declarations, adaptive layout/orientation, distribution/signing/update route and acceptance matrix. Firestick workflow exists; current manifest requires Leanback, so general phone compatibility is unverified. |
| BROWSE-014 | **Additional category/channel filters and sorting.** | Exact criteria/keys/direction, per-section/account persistence, reset/defaults and interaction with recents/Favorites/Search/adult filtering. Existing name search, TR-first ordering and SET-008 are baseline features. |
| INPUT-002 | **Mouse pointer support:** usable pointing, clicking, scrolling and playback controls. | Hover/focus, click/right-click, wheel/timeline, pointer visibility and coexistence with keyboard/remote. Generic clickable controls do not establish full support. Coordinate INPUT-001/PLAT-001/PLAY-011. |
| UI-014 | **Icon library:** a consistent reusable icon set for navigation/controls. | Coverage/variants, app-owned vectors versus library dependency, licensing, sizing/contrast/focus and accessible names. Current icons are Canvas drawings. Preserve classic launcher branding and channel fallback separately; no library selected. |

## Acceptance and evidence still to clear

Implemented entries below are not automatically defects or undecided product behavior. Host checks have limits, and some IDs have separate historical device evidence. Consult the current gaps/manual rows in [REQUIREMENTS.md](requirements/REQUIREMENTS.md) and [TEST_CASES.md](requirements/TEST_CASES.md). Superseded requirements are history, not active backlog. Future deployments/app starts require the approval in [the deployment guide](operations/FIRESTICK_DEPLOYMENT.md).

| Area / IDs | Remaining evidence or review |
| --- | --- |
| Startup/account — LOGIN-001–008, SET-004/007, UI-010 | Physical account keyboard/edit/dismissal, cold-start/Activity routing, readability/relaunch, large cached-start timing and retired-address migration. Retain separate SF-1 first-load/cache acceptance. |
| Browsing — BROWSE-001/006–013 | Actual remote repeat/Menu/Context Menu, root Cancel/Exit, full Activity recreation, empty/offscreen/density transitions and real decoder playback return/release. |
| Recent history — RECENT-003 | Installed-app restart and rapid physical playback switching/return persistence. Preserve the shared 20-entry limit. |
| Visuals/accessibility — UI-001–013, SET-006 | TV contrast/readability, all densities/resolutions, assistive naming, startup branding, channel logos and headings. Retain completed launcher migration evidence; external logo probes remain unapproved. |
| Adult filter — SET-008 | Actual remote status/persistence, lifecycle/refresh/accounts, hidden-category returns, all routes and real playback stepping. Local matching/persistence/focus checks pass. |
| Native playback — PLAY-001/002/004/005/007/009, VOD-001/002/003/005 | Picture/sound, actual protocol/user-agent requests, media keys/settings/popups, seeks/overlays, lifecycle release/resume and continuity. Separately verify inactivity beyond the real screensaver timeout. |
| Playlist/metadata — DATA-001–005 | Broader representative dialects/optional fields, real user-agent integration, provider number availability, arbitrary logo reachability/formats and TV appearance. No exhaustive format claim. |
| Snapshots/refresh — DATA-006/007, REL-001/002 | Full lifecycle/refresh continuity, backup/transfer exclusion, minified schema read-back if release is later needed, safe crash UX, heap/performance profiling. Retain historical SF-1 evidence. |
| Compatibility — REL-003 | Real device/OS/decoder acceptance beyond host remote/manifest checks; broader targets are PLAT-001. |
| Configuration/privacy — SET-001/003, PRIV-001–003 | Clean/no-private-file builds, release defaults/signing only when relevant, update retention and source/history privacy review. Debug artifacts remain private. |
| Deployment/workflow — OPS-001–007 | Clean-checkout setup, command modes/failure paths, permission conduct, real updates and publication/privacy history when requested. Completed identity migration is not pending repetition. |
| Review/testing — DEV-001–006 | Requirement/architecture approval, scope compliance and meaningful coverage review; pass totals are not product approval. |
| Remediation — FIX-001–010 | Cross-cutting physical focus/IME/player lifecycle/protocol/cache/backup/visual evidence remains bounded by each fix's catalog gaps. Source cleanup review and optional release checks are not new product features. |

## Maintenance

For a selected item, settle concrete criteria/options in the catalog, obtain implementation approval, and record the decision/evidence here. Keep stable IDs. Do not infer delivery from a build, call an undecided item Deferred, or infer service/device authorization from this list. Documentation relocation and path maintenance do not authorize backlog feature implementation.
