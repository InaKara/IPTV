# Firestick validation — 24 October 2026

## Authorization and installed build

The user explicitly approved deployment and the described device verification session, including necessary app restarts and remote inputs. They then stated that they had already deployed and confirmed:

- TRT 1 HD has sound.
- Playback controls function.
- The video remains visible behind the playback controls.

No agent installation was performed. A SHA-256 comparison confirmed the installed base APK matches the current locally built debug APK; the source checkout is commit 077e789. User-reported picture/sound/control acceptance is distinct from the diagnostic observations below. The normal Java heap growth limit is 192 MiB.

The agent performed two controlled fresh-process cached starts and one successful same-account provider refresh, preserving app data. The initial screensaver was dismissed with a remote Back input before profiling; no saver/power preferences were changed. Settings navigation and the Refresh action used normal remote inputs. An initial pointer activation did not change the download timestamp; that attempt's samples are excluded from the refresh result. No account fields were edited and no channel list was deleted or truncated.

## Android memory observations

Memory was sampled through dumpsys meminfo while the app performed real work. Java figures below are Dalvik heap allocated bytes, rather than the different Java Heap PSS summary. Native allocated bytes and total process PSS are listed separately. Maxima in separate columns need not occur in the same sample and must not be added together.

| Scenario | Samples | Highest sampled Java allocation | Highest sampled native allocation | Highest sampled total PSS |
| --- | ---: | ---: | ---: | ---: |
| Fresh process, existing saved list | 25 | 81.32 MiB | 7.93 MiB | 162.60 MiB |
| Same-account provider refresh | 18 | 146.29 MiB | 35.33 MiB | 266.19 MiB |
| Fresh process, refreshed saved list | 25 | 85.64 MiB | 8.31 MiB | 185.25 MiB |

The first saved list contained 101,095 entries. The successful refresh changed the last-download display and the next cached start loaded 101,098 entries, without provider-wait/download phases. Both starts reached visible Live TV categories. The refresh retained the same app process and completed; both session processes had no recorded fatal exception or OutOfMemoryError. No codec/sink error was recorded for those browsing processes; they did not exercise playback.

At the final idle category screen, Java allocation was 52.33 MiB, native allocation 6.11 MiB and total PSS 136.98 MiB. The highest sampled Java allocation leaves about 45.71 MiB below the normal heap limit. Total PSS includes native, graphics and mapped memory, so its 266.19 MiB sample is not a violation of the separate 192 MiB Java limit.

These are sampled maxima, not continuous high-water measurements or a proof of retained-object size. Each memory query took up to about 2.9 seconds, with a further 0.4–0.5-second interval. Brief peaks can be missed, and profiling can perturb processing/GC. No heap dump or deliberate GC request was used. There is no matched pre-MR-1 Android run, so the earlier desktop savings cannot be presented as a measured Android before/after reduction.

Connect-to-first-browse-layout timings after the separate splash were 20.968 and 20.628 seconds during sampling. Cache spans were 6.486 and 6.111 seconds; grouping spans were 10.731 and 10.777 seconds. These instrumented timings are not normal startup benchmarks or directly comparable to the older unprofiled 14.857-second sample. Refresh currently emits no new startup timing attempt when a working list already exists; successful refresh is established by its saved-download timestamp and subsequent updated cache count, not a fabricated startup span.

## MP2 and controls evidence

In the initial user-run process, fixed AypiAudio diagnostics record an MP2 stereo 48 kHz track as supported and selected, followed by decoder_initialized. AAC track selection/decoder initialization was also present in that process. Together with the user's audible TRT 1 HD confirmation, this closes the original channel's reported silence at basic user-smoke level. These fixed diagnostics do not name the selected decoder; there is no isolated MP2-versus-platform memory measurement, audible known-good-format comparison or explicit long-run synchronization result.

An additional agent playback attempt opened a Recently viewed entry matching TRT 1 HD and selected AAC stereo 48 kHz, with decoder initialization. Its highest sampled Java/native allocations were 64.02/38.45 MiB. Those samples are classified as AAC playback, not an MP2 delta. The fixed initial logs are not tagged by channel, so they do not establish which codec/renderer was used for TRT 1 HD during the user's viewing. Provider/source variants cannot be excluded, and audible TRT output alone does not prove that stream used the software fallback.

The user's control and background-video confirmations establish basic physical acceptance for PLAY-009/012/013. The first inspection incorrectly suggested a return to browsing: its accessibility XML was identical to the prior browsing snapshot even though the dump command reported success. The user confirmed nobody was using the remote. A live activity dump contained TvPlayerView and direct screenshots showed TRT still playing, disproving the reported exit. This was an inspection error; no app exit defect or implementation change is established. Native playback checks below therefore use direct screenshots and actual activity hierarchy rather than that stale accessibility snapshot.

### Direct playback checks after correcting the inspection

- Native Menu showed the amber-focused Play/Pause control over visible video. Rewind and Forward produced accepted -10 s / +10 s notices with picture retained. Two rewind presses moved behind live; a directly observed native focus path (Menu, Down, Down, Select) activated Live from a -00:22 display and returned it to -00:00 / the Live indicator. Pointer taps are not treated as proof of a button activation.
- Playback was paused for 91.3 seconds. Direct screenshots before and after the wait show the same paused frame and a Play control, with the picture still visible behind the lower panel. The rolling window's scrubber reached its left boundary while paused.
- After Play, moving pictures and a Pause control were observed at approximately 23 and 79 seconds after resumption. By the later capture, the display had returned to -00:00 / the Live indicator. No unhandled playback failure, codec/sink error, fatal exception or OOM was recorded for this process. No BehindLiveWindowException/ERROR_CODE_BEHIND_LIVE_WINDOW record was captured, so this validates practical long-pause continuation, not proof that the specific exception-recovery branch ran. Provider fallback and exhaustive oldest/newest bounds remain unprovoked/unverified physically.
- The native settings popup opened with readable amber focus, Normal speed and Auto audio. Back dismissed it, with moving video and the lower controls retained. Menu/Back hid controls, then Back returned to browsing; the live activity hierarchy confirmed TvPlayerView was removed and MainActivity remained.

The inspected playback selected AAC, so these checks do not isolate software MP2 memory or establish every source/codec variant. During sampled pause/resume segments, highest Java allocation was 74.12 MiB, native allocation 40.25 MiB and total PSS 187.26 MiB (separate per-column maxima). Audible sound after this particular long pause and precise synchronization were requested from the user but were not yet confirmed when this record was written. Existing user-confirmed TRT sound remains separate evidence. VOD and a forced provider/source fallback were not retested.

## Outcome and remaining work

The historical memory crash did not reproduce in this session. Two large cached starts and one actual refresh completed within the sampled Java budget; no immediate further memory implementation is selected by these results. Historical OOM resolution remains unproven, particularly for missed peaks, much larger lists, repeated refreshes, playback overlap and external memory pressure. Further optimization still requires a reviewed scope; this investigation does not authorize an index/cache/provider redesign.

After all viewing checks, the agent deliberately returned to browsing and verified that the native player was absent from the actual activity hierarchy, with no stream continuing. Both temporary device UI dump files were removed and the connection closed. Private UI/package/log/memory/screenshot artifacts stay under ignored app/build/reports/device-20261024/. No app source/build/deployment behavior changed, no release build was made and no commit/push was requested. Automated evidence regeneration remains distinct from these physical observations.

Final evidence/gap maintenance: fresh local smoke passed **153/153 in 32.4 seconds**, zero failures/skips/errors. No implementation test or app source changed; manual physical observations and unprovoked/error/audio gaps remain distinct from these generated automated outcomes.
