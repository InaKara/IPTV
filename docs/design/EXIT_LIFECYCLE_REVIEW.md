# EX-1 — confirmed Exit and browser lifecycle correction

26 October 2026. **Explicitly approved with approved, implement on 26 October 2026.** Scope: BROWSE-008 and REL-008 only, with selected local implementation and meaningful lifecycle/task/focus regression checks. Deployment and any app start or physical reproduction need separate approval. Read-only diagnostics did not start/stop/navigate/install the app or change settings/data.

## Confirmed evidence

After the separately approved UX-2 deployment, the user reports that Exit appeared to restart the app and then showed the crash/restart screen. The deployment script built, installed with saved data retained and reported a successful start; that was not feature acceptance.

Existing Android event records show, on the device clock:

| Event | Observation |
| --- | --- |
| 26 October, 09:37:36.097 | App-requested finish of one MainActivity record |
| 09:37:36.145 | Resume of a different MainActivity record in the same task |
| 09:37:36.272–.277 | Fatal IllegalStateException: FocusRequester is not initialized; corresponding app crash event |
| Crash stack | HomeScreen.kt:229, the sidebar requestFocus branch in BrowserScreen's focus-command effect |
| Following restart | Process dies; SplashActivity is created, consumes the crash marker and opens ErrorActivity |

This confirms both the underlying app-screen resume and a real focus crash. It does not identify exactly which earlier launch created the duplicate MainActivity. The source uses plain finish for both confirmed-Exit callbacks, ordinary Splash-to-Main starts and no single-browser reuse policy. The global exception handler records only a generic crash marker and delegates to Android; it contains no app-launch instruction. The notice accurately follows a real crash and should not be removed to hide it. Older OOM records in the crash buffer are historical; they are not the latest failure.

The current read-only task snapshot later contained one MainActivity. It cannot reconstruct the pre-exit duplicate stack by itself; distinct event-record identities supply that evidence. Raw logs remain ignored under app/build/reports/exit-investigation; no private addresses, credentials, provider contents or raw exception exports are tracked.

## Approved behavior

1. **BROWSE-008 — Exit closes the whole app task.** Keep the existing root Back confirmation and Cancel behavior. Confirming Exit closes every Aypi Tivi activity in its task and removes that task, returning to Fire TV Home rather than exposing an older browser/splash/error activity. Do not force-stop, kill the process, uninstall, clear data or reset preferences/history/progress. Removing the task means a later explicit launch enters through normal startup; there is no automatic restart after Exit.
2. **REL-008 — Safe launch and focus lifecycle.** Repeated launcher/startup handoffs reuse the existing browser where appropriate instead of stacking duplicate MainActivity instances. Keep the two-second splash for a genuine fresh launch, existing crash-notice consumption and explicit Restart behavior. Restore remote focus only after the target is attached in the active screen; invalidate obsolete commands when screens detach, stop, change or finish. Preserve sidebar/grid/search/settings/library returns and empty/offscreen behavior. This is a lifecycle correction, not removal of focus restoration or blanket suppression of exceptions.

The selected architecture is full task completion for explicit Exit plus idempotent app launch routing and attachment/lifecycle-bound focus work. Merely moving the task into the background can retain the duplicate stack; merely finishing the top activity already reproduces the problem. Explicit process killing would bypass normal cleanup and is excluded. Framework task/focus APIs will be checked against the pinned Android/Compose baseline without dependency or broad navigation migrations. Android documents [task removal](https://developer.android.com/reference/android/app/Activity#finishAndRemoveTask()) and [attached focus targets](https://developer.android.com/develop/ui/compose/touch-input/focus/request-focus); those APIs alone are not evidence of a working correction.

## Verification and boundaries

The approved verification scope covers root confirmation/Cancel/Exit integration; duplicate launch/task routing; genuine fresh versus reused launch; queued sidebar/grid/search focus across detach/pause/resume/finish; no startup handoff after closing; retained account/preferences/favorites/progress; unchanged real-crash notice/delegation. Use synthetic accounts, no provider traffic or device input. Run fresh smoke/lint and debug assembly. Before EX-1, BROWSE-008 had manual-only coverage and the component/UI tests did not cover this Activity Exit sequence. Baseline passes did not prove the reported Exit case worked.

Physical acceptance remains a separately approved session or user observation after a separately approved deployment: confirm Cancel stays usable, Exit reaches Home once without reopening/notice, a later explicit launch works, and the remote remains usable. No new timeout threshold, retry loop, UI control, data migration, audio/subtitle change, provider access, deployment-script change, installation or agent app start is selected by this proposal.

## Pre-implementation baseline

BROWSE-008 was Partial with its confirmed failure recorded; REL-008 was Open. Both had explicit pending Manual coverage. Existing local smoke passed 198/198, zero failures/skips/errors, in 38.3 seconds after reviewing the mappings and regenerating companion rows. This was baseline/catalog evidence only, before corrective implementation. Proposal links and whitespace checks passed; unrelated UX-2 checkout changes were preserved.

## Local implementation — 26 October 2026

Both requirements are now Implemented locally, with partial automated coverage and explicit physical acceptance gaps. Confirmed Exit marks its task as closing and selects the matching ActivityManager.AppTask for finishAndRemoveTask. This API removes the complete task even when the caller is not its root; Activity.finishAndRemoveTask alone would only remove a root caller's task. An already-missing task falls back to finishing same-affinity activities and removal. No process-kill, crash-marker clearing or data reset is added.

Splash and Main use singleTop. A resumed, live Splash hands off with CLEAR_TOP/SINGLE_TOP and finishes itself, so an existing Main receives the intent instead of gaining another instance. Its two-second timer is canceled on stop/disposal. Real crash markers still delegate through the existing exception handler and are consumed once into ErrorActivity.

Browser/library focus tracks placed targets without writing Compose snapshot state from layout callbacks. A single pending request runs outside layout only while its target is attached, its command/route is current and the host is resumed, attached and not finishing/destroyed/closing. Detach/disposal/lifecycle changes invalidate pending work; resume reissues the current restoration. Menu and Resume dialogs own their focus scopes. Existing sidebar, grid, offscreen, search, settings and library return behavior remains covered by the regression suite. No blanket exception catch or repeated focus retry is added.

TC-APP-159–164 add complete-own-task selection with a non-root caller/other-task/data assertions, controlled Activity handoff flags/lifecycle, actual Splash crash-marker handling, queued/obsolete/paused/removed target focus, foreground splash timing and the actual Exit dialog/browser components in a managed ComponentActivity. The dialog check uses remote Select for Cancel and Exit, verifies one Exit callback and retains preferences/favorites/progress through closing-task resume.

These are local Robolectric framework shadows and real Compose components, not Fire OS task-manager execution or a complete Hilt MainActivity duplicate stack. Early unmanaged Activity fixtures contaminated the shared Compose test clock; retaining the managed Compose test environment resolved that test interaction. API-28 SQLite fixtures close explicitly, and remote Select replaced an unreliable simulated touch in the dialog test. Temporary diagnostics/timeouts were removed. Actual duplicate-stack removal, Home, subsequent launcher reuse and physical remote acceptance remain manual and separately authorized.

Final fresh test-smoke.ps1 -Lint passed 204/204 (164 app plus 40 shared), zero failures/skips/errors, in 59.4 seconds, within the 90-second warm maintenance target. Lint has zero errors and 33 warnings; debug assembly passed separately. Requirement mappings were reviewed and generated companion/testcase rows refreshed. No Firestick command, provider access, deployment, app start, commit or push occurred during implementation. See the deployment guide for acceptance boundaries.
