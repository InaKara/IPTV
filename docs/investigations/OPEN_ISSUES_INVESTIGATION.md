# Open technical issues — investigation, 23 October 2026

The user explicitly requested investigation of the open technical issues and authorized Firestick use if needed. This covers local/source investigation, bounded provider metadata/stream inspection and read-only device diagnostics. No implementation, new dependencies, deployment, app start/stop, navigation, input or power/settings changes are included. App starts and deployments retain the separate approval requirements in [AGENTS.md](../../AGENTS.md). No TV power-on is needed for initial PC work; device reachability depends on the Firestick remaining powered and network-accessible. Visual/audio/remote acceptance needs the TV/display and user confirmation.

Scope: MP2 decoder feasibility; live seeking/provider DVR capability; provider EPG/types/library metadata; large-list memory and startup processing; format/4K evidence; lifecycle/screensaver logic and available device state. [The backlog](../backlog.md) and [requirement catalog](../requirements/REQUIREMENTS.md) retain product definitions/statuses. Findings and limits will be recorded here; authenticated responses, device logs and scratch diagnostics stay under ignored app/build/reports/open-issues/.

**Status update, 24 October 2026:** the detailed investigation below records the earlier baseline. AU-1/LP-1/MR-1/PV-1 have since been implemented locally. The user deployed the update and confirms TRT 1 HD sound, functioning controls and visible background video. An approved Android session verified APK identity and two cached starts plus one refresh without new fatal/OOM, with a highest sampled Java allocation of 146.29 MiB against 192 MiB. Samples can miss peaks; historical OOM resolution and broader individual physical variants remain unproven. EPG stays Deferred. See [the current device validation](FIRESTICK_VALIDATION_2026_10_24.md).

## Results at a glance

| Question | Confirmed result | Remaining limit |
| --- | --- | --- |
| Can work proceed with the TV off? | The configured Firestick was reachable with wakefulness Asleep and the app already running. Only read-only diagnostics were used; it was not woken. | Display/sound/remote and real playback acceptance need a viewing session. TV power itself was not queried or changed. |
| Does the service expose structured metadata? | Authorized API calls returned 95 live, 50 movie and 33 series categories; 10,113 live entries, 19,566 movies and 3,859 series. Movie details and season/episode metadata are present. | This is provider discovery, not an implemented API/library migration. Counts are a point-in-time sample. |
| Is EPG data available? | A complete XMLTV response contains 225 channel definitions and 8,773 programme entries for 22–24 October 2026. | Sparse/duplicate identifiers substantially limit exact matching; no app guide exists. The user explicitly keeps EPG Deferred in backlog. |
| Is live rewind technically possible? | Provider evidence shows about 60 seconds/six segments. Approved LP-1 / PLAY-012 now implements 10-second steps, separate Live/lag, validated provider-bound HLS/original fallback and expired-window recovery. Local rolling-HLS/native-control checks pass. | The update has not been deployed. Actual provider seeking, pause expiry, audiovisual continuity and physical remote acceptance remain unverified. No hours/days catch-up is claimed. |
| Is MP2 feasible in software? | AU-1 integrates the official extension/source-built ARM libraries. User confirms TRT 1 HD sound after deploying the matching APK; device fixed diagnostics show supported/selected MP2 stereo 48 kHz and decoder initialization. | Known-good audible comparison, synchronization, exact logged renderer identity and isolated decoder-memory delta remain incomplete. |
| Is the memory issue resolved? | MR-1 implemented. Approved Android sampling completed two cached starts and one actual refresh without new fatal/OOM; sampled Java maxima 81.32/85.64 MiB for starts and 146.29 MiB on refresh, against 192 MiB. | Historical crash did not reproduce but is not proven fixed. Queries can miss peaks; no matched Android pre-change comparison or larger/repeated/playback-overlap/pressure stress acceptance. |
| Is a 4K label proof of 4K? | One UHD-labeled sampled entry actually delivered 1920×1080 H.264/AAC. | It establishes only that sample's resolution; genuine 4K output remains unverified. |

## Provider metadata and EPG

Bounded, authorized PC requests used the configured provider and the app's user-agent. Account authentication was accepted. Category/stream/detail requests returned structured JSON; no account settings were changed and no logos/artwork were fetched.

- The live collection has 10,113 entries, including 9,911 with the standard live type and 202 with another type value. Collection/type handling still needs an explicit design; do not reinterpret arbitrary values as VOD automatically.
- Movie details in one sample include plot, cast, director, genre, duration and release date. One series-detail sample contains one season and 13 episodes, each with an episode playback ID and metadata. This supplies a concrete starting point for LIB-001 and VOD-006.
- No live API entries advertise archive support; maximum advertised archive days is zero. This does not contradict the short HLS window below, and does not establish hours/days of catch-up playback.
- The saved M3U has 100,961 entries; existing heuristics classify 10,113 live and 90,848 VOD. The current API movie count is not the M3U VOD count: series and episode structure are different collections, and the saved playlist is an earlier snapshot. No general classification-accuracy guarantee is inferred from matching live totals.

The XMLTV response was 2,670,092 bytes and parsed as a complete XML document. Its 225 channel definitions contain 170 distinct nonblank IDs and one blank definition; some IDs repeat. The 8,773 programmes reference 124 distinct nonblank channel IDs. Of 10,113 live API entries, 266 have nonblank EPG identifiers; 220 match a nonblank XML channel ID and 163 match an ID with programme entries. Those are entry counts, not unique-channel counts or a guarantee of a currently airing programme. The guide's date span was 22–24 October 2026.

Several short-EPG API samples returned empty listing arrays. Initial requests using a generic Python user-agent returned HTTP errors; repeating with the app user-agent produced accepted responses and the valid XMLTV document. Do not conclude that the account has no guide from an empty short-EPG sample. XMLTV is a demonstrated source, with sparse mapping as the principal unresolved coverage issue. External EPG services/name matching would need separately reviewed source, ambiguity and refresh rules.

## Live TV seeking and format evidence

The reported channel's sampled progressive transport stream contains 1280×720 H.264 video and MP2 stereo at 48 kHz, with no finite duration reported by the PC probe. Its provider HLS variant returned a media playlist, not a master playlist, with six segments totaling approximately 58–60 seconds across observations and no ENDLIST. A bounded 4 KiB prefix of each oldest/newest segment returned HTTP 200 and MPEG-TS sync bytes. The sequence number did not advance during the very short segment-prefix check; continuous moving-window playback was not tested.

The separate UHD-labeled sample delivered 1920×1080 H.264/AAC and an approximately 60-second HLS playlist. A label alone therefore cannot select a verified 4K acceptance source. Two bounded stream probes do not establish service-wide resolution/codec support.

At the investigation baseline, PlayerViewModel required isVod, seekability and the native seek command before allowing a seek, and hid seek commands otherwise. That policy blocked live seeking independently of server capability. The app already had HLS/DASH modules, without selecting the discovered HLS alternatives. Just removing that guard would not establish a seekable timeline for a progressive stream.

The [Media3 live-streaming documentation](https://developer.android.com/media/media3/exoplayer/live-streaming) describes seeking within an adaptive live window, returning to the live default position, and failure when a long pause falls behind that window. The baseline error listener reported only a generic playback error. Those findings informed the subsequently approved and locally implemented [LP-1 review](../design/LIVE_PLAYBACK_REVIEW.md): capability-based live seeking, Live/lag, short-window bounds, recovery and identity-preserving HLS selection/fallback. No full catch-up or local recording feature is inferred or implemented.

## MP2 fallback feasibility

The [official Media3 1.2.1 FfmpegLibrary source](https://raw.githubusercontent.com/androidx/media/1.2.1/libraries/decoder_ffmpeg/src/main/java/androidx/media3/decoder/ffmpeg/FfmpegLibrary.java) explicitly maps MPEG Layer I/II and MP3 MIME types to the mp3 decoder name. The existing desktop FFmpeg successfully encoded a synthetic two-second 48 kHz stereo MP2 tone and decoded it using that MPEG audio decoder, producing 387,072 PCM bytes with nonzero peak/RMS. This demonstrates the desktop decoder family, not the Android extension's packaging or acceptance.

The [version-matched extension build instructions](https://raw.githubusercontent.com/androidx/media/1.2.1/libraries/decoder_ffmpeg/README.md) require a local native source build rather than a normal Maven dependency, recommend FFmpeg 6.0 and describe tested NDK versions. Native Windows builds are unsupported by those instructions. No distribution was started and no native tools/sources/dependencies were installed during the initial investigation. Subsequent approved AU-1 implementation used workspace-local WSL tooling with pinned FFmpeg 6.0.1/NDK r26b; the configured global SDK was not upgraded. See [FFMPEG_BUILD.md](../operations/FFMPEG_BUILD.md).

Read-only device facts: Android API 28; supported ABIs armeabi-v7a/armeabi. An arm64-only decoder would not load on this runtime. AU-1 subsequently built/packaged armeabi-v7a and arm64-v8a with platform-first audio preference. Source/native/build/packaging evidence is recorded in the audio review and build guide; loading and audible acceptance on the actual Firestick remain open.

Earlier configuration inspection found MP3 but no explicit MP2 declaration. The sampled process logs contained no fixed AypiAudio records, so actual device support/selection remains unknown. No app launch, playback change or audible test was performed during the investigation or later local implementation. Software decoding is now bundled locally; PLAY-008 and PLAY-010 remain Partial because device audio acceptance is pending.

## Memory and startup processing

Read-only device configuration reports a 192 MiB normal heap growth limit and 384 MiB maximum heap setting. The latter is not the current normal app budget or a justification to enable largeHeap. The existing process was sampled while asleep; this is not a startup/refresh peak measurement.

A standalone Java 17 diagnostic invoked the current compiled InputStream parser and BrowserCatalog using the existing private 100,961-entry, 28,712,096-byte playlist. It also exercised streaming Gson snapshot-shaped JSON, recent-history indexing and the reference copies used for home groups. Runs were capped at a 192 MiB desktop heap. No app/test source or build configuration changed.

| Phase | Observed PC duration | Retained Java heap after forced GC | Cumulative allocations during phase |
| --- | ---: | ---: | ---: |
| Parse saved M3U, initial process | 1,505 ms | 52.60 MiB | 203.91 MiB |
| Create canonical catalogue, after parsing | 403 ms | 73.60 MiB total | 204.47 MiB |
| Add history index/home reference copies | 28 ms combined | 79.85 MiB total | About 7.13 MiB combined |
| Read saved JSON in a separate cached-start process | 616 ms | 55.05 MiB | 103.41 MiB |
| Catalogue after cached read | 509 ms | 76.21 MiB total | 211.09 MiB |
| Refresh with old source/catalogue still retained | 626–1,195 ms parsing; 252–260 ms catalogue | 150.84–153.47 MiB total | About 193–200 MiB for each refresh parse/catalogue phase |

Allocations are cumulative bytes created, not simultaneous RAM requirements. Retained figures are after forced collection in desktop HotSpot; Android object layout, GC, native/graphics memory, I/O and CPU differ. The benchmark's summed per-pool peak counters are not a simultaneous whole-heap peak and are intentionally not used as such here. Initial and cached scenarios with one overlapping refresh completed at the desktop cap. An earlier exploratory test that retained two generations and read a third snapshot exhausted that heap; this was an artificial extra-generation stress case and is not a reproduction of the app's normal startup or historical crash.

Source review identifies plausible pressure points: metadata strings retained for the complete list, per-entry stable-ID hashing/identity temporaries, catalogue/index/reference copies, and old/new collections coexisting during refresh and stable playback. Streaming snapshot read avoids a whole JSON string, but still materializes the full object graph. The snapshot-shaped file was 31,901,582 bytes. Roughly 151–154 MiB retained in the overlap model leaves limited nominal room under a 192 MiB cap for other Java objects, but cannot quantify the actual Android margin.

These results support prioritizing allocation/overlap profiling and reviewing redundant retained structures. They do not select an optimization or prove which allocation caused the old crash. The previous device cache/grouping trace (5.010/7.903 seconds within a 14.857-second post-splash startup) remains the relevant historical device sample. PC timings are not a device speedup or target. Fresh Android cold-start/refresh peak traces need a separately approved app-start/profiling session.

## Lifecycle, screen-on and verification boundary

Source review confirms that PlayerScreen owns playback through LifecycleStartEffect: stop/disposal suspends/releases the engine, while VOD position/play intent are retained within the current ViewModel. PlaybackDisplay owns only the view keepScreenOn flag while the lifecycle is started and playback is playing or requested/buffering, and restores the prior flag on close. Existing synthetic lifecycle/remote/player-policy tests cover these branches. Persistent VOD watched/progress state is still VOD-006, not delivered by lifecycle resume.

No current device stream was started or allowed to run across the screensaver timeout. Actual Fire TV power/HDMI behavior, picture/sound, 4K output, pause expiry and remote acceptance remain open physical checks. The TV can remain off for the work completed here. No display wake, app start/stop, install, navigation, input or settings change occurred.

## Next reviewable work

Updated after the later approvals and local implementations. These five items are no longer all pending proposals:

| Original item | Current position | Next action |
| --- | --- | --- |
| Memory/startup | MR-1 is implemented and the 24 October user-deployed APK matches the local build. Two cached starts and an actual refresh reached categories without new fatal/OOM; highest sampled Java allocation 146.29 MiB of 192 MiB. | No immediate additional memory implementation selected. Keep historical risk open for missed peaks, larger/repeated/pressure/playback-overlap scenarios; use [the device validation](FIRESTICK_VALIDATION_2026_10_24.md), not desktop figures as an Android before/after claim. |
| MP2 fallback | AU-1 implemented; user confirms TRT 1 HD sound. Android selected/supported MP2 and decoder-initialized diagnostics support basic acceptance. | Known-good audible comparison/synchronization and isolated decoder-memory/precise renderer evidence remain incomplete. No feasibility/dependency approval remains pending for this implemented scope. |
| Live seeking/HLS/recovery | LP-1/PV-1 implemented and user accepted. Direct native checks confirm accepted 10-second commands, remote Live return from about 22 seconds behind, visible paused frame across 91.3 seconds and resumed moving video through about 79 seconds, settings/Back and player release. | The specific behind-live-window exception and provider fallback were not provoked; exhaustive source/window/VOD and audible post-pause synchronization remain separate. The apparent player exit was a stale accessibility snapshot, disproved by native hierarchy/screenshots. |
| XMLTV/EPG | EPG-001 is Deferred by the user's explicit decision. Discovery is complete enough for a future design discussion. | Keep in backlog until the user selects it again. No current implementation task. |
| Library metadata and VOD progress | LIB-001 / VOD-006 remain requested backlog features, without a selected detailed design. Provider movie/season/episode metadata is confirmed. | When selected, review browsing/details/episode structure and watched/resume rules. Prefer loading details/artwork on demand to limit memory; do not silently migrate caches/accounts. |

The user has already deployed and confirmed the basic audio/control/picture fixes. The approved device-memory session is complete; no additional deployment or memory redesign is selected now. Remaining individual audio/live/longer stress checks should be scoped if the user requests them, with any future deployment/app start separately approved. Earlier unconfirmed UI/lifecycle variants and future library/VOD-progress features remain in [backlog](../backlog.md); EPG stays Deferred.

Provider/device observations are point-in-time evidence; private raw artifacts and standalone diagnostics stay in ignored build reports. A label alone does not add 4K processing/data cost; genuine 4K output remains a separate physical test if selected.

## Local verification

The original investigation's fresh smoke passed **139/139** in approximately **33 seconds**, without app/native implementation. Subsequent approved AU-1/LP-1/MR-1 work brought fresh smoke with debug lint to **150/150**, zero failures/skips/errors; lint 0 errors/31 warnings. Debug assembly, verified native ABI/license packaging and local native decoding checks passed separately. All 110 requirement mappings remain current. These checks do not run provider/device probes or establish installed playback, audible sound or a memory-crash fix. The local updates have not been deployed.
