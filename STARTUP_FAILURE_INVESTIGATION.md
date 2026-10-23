# Startup crash notice and channel-list failure — 23 October 2026

Investigation of the user's report after the latest approved deployment. The user explicitly authorized repeated stop/start/deploy operations for this investigation session. During initial diagnosis, before SF-1 approval, no app source/implementation tests were changed and no new APK was deployed. One unchanged-app restart was performed; no uninstall, app-data clear or device preference change was used. Raw logs, account comparisons and provider responses remain under ignored app/build/reports/investigation/; no selected external logo URLs were probed.

## Crash notice: an earlier memory failure

The crash buffer records one app process with several fatal failures on **22 October at 22:43**, before the installed package's **23 October 09:20** update. The first fatal failure is OutOfMemoryError; another thread fails inside M3UParser.parse on OkHttp's worker. The managed heap ceiling is **201,326,592 bytes (192 MiB)**. Additional binder/finalizer failures belong to the same exhausted process, not proof of several independent application bugs.

GlobalExceptionHandler writes a boolean in the separate crash_notice preferences. SplashActivity consumes that flag on the next launch and presents ErrorActivity, whose Restart app action opens SplashActivity again. install -r preserves that marker. Restoring aypi_tivi_prefs does not restore/remove this separate preference file. Thus a launch following the update can show last night's notice even when the updated process has not just crashed.

**Attribution limit:** the available stack identifies memory exhaustion during playlist parsing, but does not establish which playlist was in use or exactly why the live heap was full. It is not justified to attribute the crash conclusively to the synthetic fixture, provider availability, or a specific cache allocation. The underlying large-list memory risk remains unresolved and requires representative Android heap/retention profiling before claiming a fix. A desktop parser run is not equivalent evidence for Android heap use.

The notice is no longer pending. Available app-tag logs contain no fatal exception dated 23 October, including the controlled restart. This supports a historical notice rather than a newly reproduced startup crash; it is not a guarantee against future memory exhaustion. Do not suppress the crash handler or silently discard future notices to hide the underlying issue.

## Connection failure: cache validation rejects valid records

The current saved server, username and password match private local configuration; the server is not loopback and the saved user is not a synthetic fixture account.

One direct provider request from the development computer returned HTTP 200, a valid M3U header and a complete **28,712,096-byte** response. Headers arrived in **2.976 s**, with full transfer in **4.335 s**. These are PC measurements, not Firestick timings. The current compiled InputStream parser read the private response successfully: **100,961 channels**, **95 blank category values**, no null category values, no blank names and no blank stream addresses; **179 distinct groups**. No account/channel names or authenticated addresses are included here.

The existing parser accepts and preserves empty group-title values; these entries still have channel names and stream addresses. However, ChannelSnapshots.kt currently requires every groupTitle to be nonblank both when reading a snapshot and before writing it. The write validation rejects the entire otherwise valid list. LoginViewModel catches that exception and displays the generic channel-loading failure, so a successful download looks like a server/account failure. Snapshot reading would reject the same records if a matching file existed.

The two real-device attempts align with this path:

| Attempt | Cache lookup | Response wait | Download/parse | Total to failure |
| --- | ---: | ---: | ---: | ---: |
| User's launch | 1,205 ms | 3,205 ms | 15,361 ms | 19,803 ms |
| Controlled restart | 1,805 ms | 3,438 ms | 15,330 ms | 20,632 ms |

The failed outcome follows the completed download/parser span by roughly 18–33 ms. Timings alone do not identify an exception; the decisive local data/source comparison is that all 95 blank groups violate the snapshot write guard. No raw exception logging was added. An active VPN transport was observed; this does not explain a deterministic local validation rejection or justify changing device VPN settings.

## SF-1 proposed correction and validation

Affected requirements: **DATA-006 and LOGIN-008**. Accept empty optional category metadata in otherwise valid snapshots, preserving channel records rather than rejecting the complete playlist. Apply the same rule to reads and writes. Keep mandatory channel/stream, schema/account, nonempty-list, atomic replacement, cancellation and privacy checks.

After catalog approval, extend synthetic snapshot round-trip/rejection cases and cached startup coverage to include blank categories, run fresh smoke/lint, and verify the accepted account reaches Categories on the Firestick under the session's existing deployment/restart authorization. Confirm a subsequent fresh-cache start makes no playlist request. Do not embed provider data in public fixtures or turn real-provider requests into smoke tests.

The earlier memory exhaustion remains separate. Profile actual Android peak live/retained memory for a representative large initial load, refresh and cached start before selecting a memory change. Do not silently truncate channels/metadata or treat a larger heap flag as a proven fix. Any further product/architecture scope goes back through requirement review. No memory fix or large-directory device acceptance is claimed by the current 120-case host suite.
## SF-1 approved implementation — 23 October 2026

The user explicitly approved SF-1. FileChannelSnapshots now applies one shared usable-record rule to reads/writes: required name/stream values remain nonblank, category strings must be present but may be empty/whitespace. Schema/account, nonempty-list, atomic-write and cancellation checks remain intact; no category renaming or metadata/channel truncation was added.

Extended TC-APP-065 round-trips empty/whitespace categories exactly; TC-APP-066 rejects null/missing required category fields. New TC-APP-081 runs synthetic HTTP/parser/file-cache/browser startup, then a recreated session reading the same snapshot with no second HTTP request. The test explicitly waits for the replacement composition before checking startup. Fresh smoke/lint passed **121/121** (81 app plus 40 environment/project/runner), zero failures/skips/errors, in 36.0 seconds. The optimized release build also passed locally; release runtime remains unverified.

The unchanged deployment script then installed/launched the fix under the existing session approval, preserving app data. The real provider list of **100,967 channels** downloaded, passed snapshot validation and reached history preparation. The Firestick display was asleep during verification; the successful model state correctly remained unrendered until the display was awakened. After waking, UI hierarchy evidence confirmed Categories and no connection error, and diagnostics ended with browse_ready for all 100,967 records. The first-run total includes that idle interval and must not be reported as an app processing benchmark. Cached-start verification is recorded separately below.
## Device acceptance of SF-1

After the full provider list reached visible Categories, a fresh app process started from its saved snapshot. Its debug trace contained cache/history/grouping/browse_ready phases and **no provider_wait or download_parse phases**. UI hierarchy evidence again confirmed Categories and no connection error; neither verified process recorded a new fatal crash. Saved account fields were unchanged and every pre-existing recent-history reference was preserved. Only investigation-created UI dump files and the temporary debugger bridge were removed; app/cache/preferences were not cleared.

| Cached-start phase | Observed duration |
| --- | ---: |
| Local snapshot read/validation | 5,010 ms |
| History preparation | 247 ms |
| Browser indexing/grouping | 7,903 ms |
| Main startup trace to first Categories layout | 14,857 ms |

This is one real-device sample for **100,967 records**, not an average or a performance guarantee. The trace begins after the separate two-second splash; its phase durations do not sum to total because scheduling/rendering remain. The first download trace's 405,195 ms total includes a long asleep-display interval and is not a valid processing benchmark. Startup while the display was asleep correctly deferred UI collection/indexing until foreground resume; no extra UI-transition fix was necessary.

SF-1 is implemented and its first-load/fresh-cache acceptance passed on the Firestick. Real playback, long screensaver inactivity checks and general large-list refresh/heap safety were not tested by this acceptance. The historical memory crash remains unresolved; two successful starts do not establish its cause or eliminate its risk. Session-specific device authorization does not replace the standing approval rule for future unrelated deployments/starts. Changes remain local; no commit or push was requested for this fix.