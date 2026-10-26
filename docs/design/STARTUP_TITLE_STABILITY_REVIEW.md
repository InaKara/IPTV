# ST-1 — stable startup title

26 October 2026. **Explicitly approved with approved, implement after the concrete revision/in-chat overview.** UI-017 correction and local implementation/tests are selected. The user reported that Aypi Tivi changes position and size during startup after the separately approved EX-1 installation/start. This is a title/layout report, not acceptance of Exit/Home or other deployed behavior. No ST-1 deployment, device/provider access, commit or push is authorized.

## Findings and coverage gap

Before ST-1, the initial app launch window used splash_background.xml with the existing startup_brand_name vector in a fixed 200 dp by 40 dp box. SplashActivity and subsequent loading/error stages used StartupBranding with a MaterialTheme.typography.displayMedium Text for the name. Its font metrics and measured line height also determined the centered branding column and status placement. Those different renderers and geometry supplied a source-supported explanation for the reported jump. No device recording established the precise frame or ruled out an additional window-inset difference.

Before ST-1, TC-APP-158 checked the Compose logo size/position across loading/error and the native launch background/title color, but did not compare the visible title's shape, size or position across native launch and Compose. TC-APP-082 also checked logo stability, not that handoff. Existing passes did not validate title continuity. The proposal marked UI-017 Partial and retained passing cases with an explicit gap instead of relabeling assertions they never made.

## Approved correction — UI-017

- Keep Aypi Tivi at the same visible size and position from the first app-drawn launch screen through the two-second splash, saved-list/connection/loading and branded error stages. Status text and the existing Open Settings action occupy reserved space below the branding.
- Use the initial launch title as the reference: the same existing burgundy title artwork, nominal 200 dp by 40 dp, in every stage. Keep the classic nominal 320 dp illustration, cream #E5E4C6 background, burgundy #500B14 lettering and current initial launch placement. Share the branding dimensions/alignment between native and Compose and fit the whole block consistently when available space is smaller. Do not independently recenter or resize the title when a status/action appears.
- Preserve the current two-second foreground splash, startup/cache/network behavior, explicit Settings/error retry, remote focus, dark browser, launcher icon/banner, EX-1 task/focus correction and saved data. Add no delay, animation, startup-stage removal, replacement artwork, dependency or deployment change.

The recommended approach reuses existing vector artwork and a shared layout specification. It avoids making fixed launch artwork imitate separately measured themed text through approximate offsets. The branding name will have a fixed visual size independent of the system text-size setting; status/error text and controls remain readable text, and the displayed title retains an accessible name. The initial title's lettering and size are preserved; the later stages change to match it. The existing textured illustration/faint edge is outside this correction.

## Selected verification

Extend meaningful native/Compose rendering checks to compare actual title ink bounds and appearance, plus logo/title alignment, across the launch drawable, splash, saved-list/connection/loading and branded error stages at supported TV sizes/densities. Include nondefault text scaling to check that status/error controls still fit while the branding remains continuous. Retain existing startup routing/timing, error-action and remote-focus regressions. Run fresh local smoke/lint and debug assembly after implementation; do not infer device acceptance from those checks.

Physical startup observation after a separately approved installation remains manual. No Firestick commands, app starts, recording, navigation or provider access are part of this implementation approval. Deployment still requires its own explicit approval; no commit/push is selected.

## Pre-implementation review evidence

At review, UI-017 was Partial with ST-1 pending. The mapping retained existing assertions with the missing cross-stage title comparison recorded. Fresh test-smoke.ps1 passed 204/204 existing checks (164 app plus 40 shared), zero failures/skips/errors, in 43.7 seconds after mapping review and generated-row maintenance. That was catalog/baseline evidence only, before corrective code/tests. Project-contract installation messages were local mocks; no device action occurred.

## Local implementation and validation — 26 October 2026

StartupBranding draws the same splash_background resource used by the real Splash window, through a noninteractive Android View under the existing Compose status/error controls. There is no separately measured title Text. The shared artwork exposes the app name through Compose accessibility semantics. Foreground text remains normal scalable text; the name remains fixed artwork.

Shared dimension resources preserve the original normal-TV initial geometry: 320 dp illustration, 200 by 40 dp title, unchanged native center offsets. Both native and Compose select compact dimensions below 360 dp width or 520 dp height: a 160 dp illustration and 100 by 20 dp title, placed higher as one block to reserve space for a two-line error and retry. There is no status-dependent recentering, font-size override, delay, animation or artwork replacement. The original textured JPEG and its possible faint edge remain. Existing two-second lifecycle timing, setup/retry, browser focus and EX-1 routing are unchanged.

TC-APP-165–167 render the actual background resolved from SplashActivity's packaged theme, and compare its title ink bounds, complete title pixel shape and full illustration/title region with actual splash, saved-list, connecting, loading and branded-error UI. Normal 960 by 540 mdpi and 1920 by 1080 xhdpi renders retain the independently asserted original placement. The high-density and compact 568 by 320 cases simulate 1.3x Compose text scale and verify accessible title, separate readable status, fully visible focused retry and remote Select reaching Settings. TC-APP-158/082 now compare real artwork rather than logo-only semantics; TC-APP-034 resource checks remain.

The initial compact check found a real larger-text error-action overflow. Adjusting only the shared compact placement corrected it; title/illustration pixels remain identical across stages. All 11 focused startup/manifest/lifecycle checks pass. Normal, high-density and compact error renders were visually inspected under ignored app/build/reports/st1. No device/provider operation, deployment, commit or push occurred. Local render checks do not prove physical Fire OS starting-window/inset continuity.

Final fresh test-smoke.ps1 -Lint passed **207/207** (167 app plus 40 shared), zero failures/skips/errors, in **53.9 seconds**, within the warm maintenance target. Lint: zero errors/33 warnings. Debug assembly passed separately; requirement mappings and generated test rows are current. An earlier full run timed out in existing TC-APP-136 while waiting for library detail Retry; Gradle then did not execute its eight environment fixtures. That same testcase passed unchanged in isolation, then the complete fresh run passed. No library code/test was changed for the timeout; its cause was not established. The latest local build/checks have no outstanding failure, and physical startup acceptance remains unrun.
