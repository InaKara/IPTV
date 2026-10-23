# Automatic startup, playlist refresh and playback display

Revision SC-1, 22 October 2026 — **explicitly approved: “Approved, implement.”** The preceding baseline was pushed as `96f7b4e`. Implementation/verification status is tracked in REQUIREMENTS.md; this document preserves the approved behavior and validation scope.

## Requested outcome

Open the app into its saved channel list without pressing Connect. Refresh the list in the background when its last successful download is more than 24 hours old. Put account editing, manual Refresh and existing density controls in the separate Settings screen. Prevent screensaver interruption during video playback using Android's standard mechanism.

Affected entries: **LOGIN-002/003/008, DATA-006/007, SET-006/007 and PLAY-007**. Existing parser, account isolation, favorites/history, browser focus and playback-release requirements remain applicable.

## Approved behavior

- **Usable saved list:** read the current account's private snapshot and show Categories without waiting for a provider request or requiring Connect. Startup depends on local loading, not the network; it is not a promise of zero loading time.
- **More than 24 hours old:** show that list first, then request one background refresh. Evaluate age at startup and foreground return. No scheduled downloader runs while the app is closed. A fresh snapshot makes no automatic provider request. Invalid timestamps are stale; failed attempts never reset the successful-download time.
- **No usable saved list:** automatically download using complete saved settings or local debug defaults. Show loading off the UI thread. Missing settings open Settings account setup. Initial load failure shows a safe error and a route to Settings retry/edit.
- **Successful refresh:** validate a nonempty playlist, save the snapshot atomically and update browser data. Retain view, query, focus and scroll by stable IDs where possible, with a valid fallback for removed entries. Do not interrupt an existing stream merely because its directory refreshed.
- **Failed refresh:** retain the last working snapshot, its successful-update time and usable browsing. Show safe status without retry loops. Manual Refresh or a later startup/foreground return can retry.
- **Settings:** retain density choices and their return behavior. Add server URL, username, masked password, **Save and load**, **Refresh channel list** and last-success/refresh status. Manual Refresh ignores the 24-hour age. Show progress and disable duplicate requests. Do not duplicate these controls in the browser or add unrequested settings.
- **Account edits:** validate before requesting. Save changed settings only after their new list loads successfully; failure/cancellation retains the previous working account/list. Cancel incompatible work when switching accounts; late responses cannot replace current data. Keep favorites/history account scoped. Focus alone must not open the keyboard; explicit Select/Enter edits fields.
- **Display:** keep awake while the foreground player is playing or buffering with playback requested. Allow normal idle behavior while paused, ended, failed, browsing, in Settings or outside the app. This interprets the report as interruption during video viewing; keeping all menus awake would be a different policy.

## Approach and tradeoffs

Reuse the current Kotlin/Compose/ViewModel/OkHttp/Gson stack. Prefer a versioned app-private file containing parsed channel records and success metadata, written atomically off the main thread. This avoids downloading/reparsing provider M3U on every start without introducing a database/toolchain migration. A large snapshot still takes time to read/deserialize/group: measure cached startup before claiming an improvement. Indexed database loading could be considered later if measurements justify the extra architecture.

Snapshots can contain authenticated addresses. Keep them account scoped and excluded from backup/transfer, Git, public examples, logs and saved-instance bundles. Preserve empty release defaults and private local configuration.

Use the foreground activity/view's standard keep-screen-on facility, tied to player/lifecycle state. [Android documents this for TV Ambient Mode](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on), and [Amazon documents it for Fire TV playback](https://developer.amazon.com/docs/fire-tv/faq-general.html). It needs no permanent device-setting change, simulated remote input or screen wake-lock permission. This supports the Android platform mechanism; it does not imply physical acceptance on every vendor's device.

## Validation after approval

Add inexpensive local cases for fresh/stale/missing/corrupt/wrong-account snapshots, the exact 24-hour boundary, success/failure/duplicate refreshes, atomic-write failure, stale responses after account changes, startup/error routing, Settings editing/focus, preserved browsing/playback state and display-flag cleanup. Reconcile old Connect-focused assertions with Settings setup, retaining meaningful checks and stable testcase IDs where appropriate.

Run the requirement-linked smoke suite after implementation. Separately measure cached startup using the same large local fixture and verify local video playback outlasts the Firestick's screensaver timeout. Verify pause/Back/background restore normal idle behavior. Slow device inactivity checks remain outside the quick suite; no external channel-logo probes are needed.

SC-1 is implemented with local regression assertions and explicit remaining device gaps in the catalog. A test update was installed before the user required consultation before every deployment. Further device validation is paused; no cached-start performance or screensaver inactivity result is claimed. Original settings were subsequently restored exactly with explicit user approval, and the app was left closed. Final local smoke/lint passed 120/120 checks; debug/release builds and release lint passed separately. The installed test build predates final local refinements. Implementation approval does not authorize another deployment.
