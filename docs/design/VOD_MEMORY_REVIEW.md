# MP-1 — VOD playback memory budget (approved)

24 October 2026. The user explicitly approved MP-1 / REL-006 with Approved, implement after the concrete revision and in-chat overview. Local implementation/checks are selected; deployment and device retesting remain separate. INV-VOD-1 authorized investigation of the installed app, not this playback change or deployment. See [the observed failure](../investigations/VOD_SEEK_2026_10_24.md).

## Evidence and interpretation

The reported MP4 has a positive seekable duration and accepts nonzero server ranges. On the installed MD-3 app, normal playback showed video, the first accepted forward seek was followed by ERROR_CODE_IO_UNSPECIFIED, the runtime repeatedly exhausted its 192 MiB Java heap, and Resume was followed by an am_crash event explicitly identifying java.lang.OutOfMemoryError. A fresh idle browser later reported 53,932 KiB (52.7 MiB) Dalvik heap allocation. This is current playback evidence, distinct from the historical startup/parser OOM and from the previously diagnosed VOD-root ANRs.

At the investigation point, PlayerViewModel did not supply a LoadControl. Inspection of the pinned Media3 1.2.1 artifact confirms 50-second min/max defaults and automatic video/audio byte targets of 131,072,000 plus 13,107,200 bytes (137.5 MiB combined). These are allocation targets, not proof that every byte was allocated in this session. The file's front moov index is about 21.8 MiB (22,808,150 bytes). Idle state plus a potential full default sample buffer already leaves very little room in the normal heap; index/parser/track state is additional. Repeated failed roughly 64 KiB allocations are consistent with playback-buffer pressure, but this investigation did not obtain a heap ownership profile. Do not claim the entire heap belongs to ExoPlayer or that this change is already a proven fix.

## Approved user behavior and defaults

REL-006 selects an explicit **24 MiB VOD sample-buffer target**, shared across selected renderer types, with size taking priority when the target is reached. Use a 10-second minimum / 30-second maximum time policy; retain the existing playback-start/rebuffer thresholds. Apply it to library and legacy VOD movies/episodes, preserving the current Live TV buffering policy. Media3's target can be exceeded temporarily by in-flight data and other allocations; it is not a hard whole-app memory cap.

The movie's requested quality/resolution, audio decoder selection, seek steps, actual timeline validation, watched thresholds, saved progress, identities, favorites and account rules stay unchanged. A smaller buffer retains less video ahead of the current position, so an unstable connection or high bitrate can produce more buffering. It does not deliberately lower quality or change the supplied source.

Keep playlist/catalog data, native/video/index allocations and application overhead outside this sample-buffer target. Keep the normal Android heap setting; do not enlarge the process heap, add a decoder/dependency, download whole films or change startup/catalog storage under this scope. Do not add automatic retries after memory exhaustion. The initially paused Play observation and future generic-error wording are separate follow-ups; no new Play-intent/reset/error UI behavior is selected here.

## Acceptance and validation

- The actual player construction uses the explicit VOD policy for both library and legacy paths; Live TV retains its baseline policy. Use meaningful local behavior checks against the pinned DefaultLoadControl, filling a synthetic allocator to its target and verifying size-priority loading decisions without private streams or large slow/device tests.
- Preserve the existing remote/native seek, resume/timeline, pause/release, progress/account and live-window regressions. Run fresh smoke/lint and debug assembly after selected approval. Report synthetic behavior as local evidence, not proof of physical memory safety.
- After a separately approved installation and playback session, repeat the failing movie's initial play, forward/back seek and Resume, compare a user-confirmed working control, and sample Java heap before/during/after playback. Require sustained successful continuation without new OOM before claiming this device failure fixed. If pressure remains, profile retained player/index/application allocations and return any further change for review rather than expanding scope silently.

## Approved scope

The user approved MP-1 / REL-006 local implementation and synthetic checks for the explicit 24 MiB VOD target and 10–30 second policy. This is a focused first correction supported by the observed memory pressure, with physical success still to be established. Deployment, device retesting, broader memory redesign and additional visible behavior changes remain separately authorized steps.

Catalog-maintenance verification after recording INV-VOD-1 and the pending MP-1 proposal: fresh local smoke passed 186/186 (146 app plus 40 shared), zero failures/skips/errors, in 57.9 seconds. No app/implementation-test changes were made for MP-1; this is unchanged baseline evidence, not a memory-fix or physical acceptance result. Device testing has ended and no additional device action followed the user's status question.

## Implementation

PlaybackBuffering.playbackLoadControl returns a real DefaultLoadControl with the selected VOD target/time/size-priority settings and the pinned default start/rebuffer thresholds. PlayerViewModel supplies this policy at the common engine-construction point using channel.isVod, covering library and legacy VOD. Live TV receives the unchanged default policy. No media-item/quality/renderer/decoder/progress/seek/error/retry behavior or deployment script was changed.

TC-APP-147 allocates real 64 KiB blocks to 24 MiB, checks loading stops by byte priority below the time minimum, verifies high-bitrate start at the full target and releases the allocator for reuse. TC-APP-148 checks the 10–30 second hysteresis and unchanged startup/rebuffer/live decisions against the pinned default. TC-APP-149 captures load control from actual PlayerViewModel-created legacy/library/live engines and verifies their loading decisions. Focused execution with the existing native live/progress regressions passed; final fresh full verification is recorded below. These are local fixtures, not actual source/device memory acceptance.

## Local verification — 24 October 2026

Fresh test-smoke.ps1 -Lint passed 189/189 (149 app plus 40 shared), zero failures/skips/errors, in 85.8 seconds. Debug lint: zero errors/32 existing warnings; debug assembly passed separately. The initial fixture allocator release API mismatch was corrected before the passing runs. No Firestick/provider action, deployment or new device evidence occurred. Implementation remains local/uncommitted. The actual movie memory/seek/Resume failure is not yet confirmed fixed; local byte/time/loading decisions are distinct from physical acceptance.

After updating the related VOD-009 approval/physical-acceptance gap, a fresh catalog-maintenance smoke also passed 189/189, zero failures/skips/errors, in 56.5 seconds. These are the same 189 unique cases, not additional test coverage. The preceding lint/debug-build results remain separate; no further app or device change occurred.
