# Playback picture visibility — PV-1

24 October 2026. The user reports that opening playback controls makes the picture unusable for checking rewind/forward. The user explicitly replied approved, implement on 24 October 2026, approving PLAY-013 local implementation and checks. Deployment remains separate; PLAY-009 and LP-1 retain their previously approved behavior.

## Source findings and limits

TvPlayerView uses Media3 1.2.1's default controller layout. The bundled exo_player_control_view.xml adds exo_controls_background across the player using exo_black_opacity_60 (#98000000, approximately 60% black). Rewind, Play/Pause and Forward sit at the centre. The bottom time/settings bar has a separate translucent background. The app currently styles focus but does not remove the full-picture dimming or reposition the centre controls.

Existing local controller previews use a black background and a synthetic player. They verify controls and focus, but cannot establish picture visibility. Source review identifies full-screen dimming as a concrete contributor; it does not prove whether the reported installed build also blanks a video surface. No Firestick, provider or app-start action was used for this review.

## Approved visible behavior

- Keep the picture at its normal brightness outside a compact bottom controller panel. Opening controls must not add a full-screen veil, replace the frame, hide the video surface, resize the video or change play/pause intent. Paused video keeps its current frame visible.
- Put Rewind, Play/Pause and Forward in that lower panel, with the seek bar, existing settings access and Live/lag or VOD times. On the supported TV landscape layouts, the panel occupies at most the bottom quarter of the player, leaving at least the upper three quarters unobstructed by the controller. Use a translucent dark background only within the panel for readable white text/icons and amber focus.
- Keep all existing actions and enabled/disabled rules: 10-second on-screen/dedicated seeking, actual live-window bounds, Live return, VOD times/shortcuts, Menu initial focus, D-pad navigation, Back dismissal and the three-second interaction timeout. Channel/seek/recovery notices must not cover actionable controls; keep their existing duration and meaning.
- Apply the same picture-visibility rule to live and VOD, playing and paused. Settings popups retain their local readable panels and existing behavior. This does not add thumbnails, scrubbing previews, recording, touch or mouse support.

Reuse the current Media3 controller widgets, IDs and player integration. Moving their layout and limiting shading to the bottom keeps existing playback and focus logic available; the tradeoff is a small area of video covered by readable controls. No new dependency or player migration is proposed. The translucent panel can overlap bottom captions while open; Back or the timeout restores the full unobstructed picture. Subtitle repositioning is not included in PV-1.

## Acceptance and verification after approval

1. Render the actual controller over a bright, multicolour local frame fixture. Compare controller-hidden and controller-visible pixels outside the bottom panel; they must remain unchanged. Inspect the native hierarchy for panel bounds at 960 x 540 and 1920 x 1080. Check focus states and localized contrast over both bright and dark backgrounds. A black-only preview is insufficient.
2. Verify live/VOD and playing/paused fixtures retain their video surface and player state while controls open, focus moves and seeks occur. Preserve the existing key, seek-capability, Live/timing, popup, timeout and Back regression checks. Place short notices clear of actionable controls.
3. Run fresh local smoke/lint and assemble the debug APK. Record rendering and automated evidence separately from actual moving-video acceptance.
4. After separately approved deployment and an explicitly scoped viewing session, confirm the picture stays visible during control navigation and repeated seeking on Firestick, including a paused frame, Live return and settings dismissal. Physical moving-video visibility cannot be accepted from a painted local fixture alone.

Decision recorded: PV-1 / PLAY-013 local implementation explicitly approved on 24 October 2026. No deployment or device/app-start permission is included.

## Implementation and local evidence — 24 October 2026

The existing Media3 widgets and IDs now sit over a 128dp lower panel with a 70% black background. The full-picture background is transparent. A 52dp transport row, 24dp timeline row and 52dp time/settings row fit inside the panel; native TV padding is cleared from the transport row. Native seek, Live, settings, key/focus and timeout logic is retained. The visibility callback lifts both Compose notice rows by the same amount, clearing the panel while preserving their separation and event-based duration; the callback is cleared on view release.

TC-APP-111 compares every pixel above the panel with the same bright/dark multicolour frame before drawing the actual native controller. Live and VOD pass at 960 x 540 mdpi and 1920 x 1080 xhdpi; all actionable controller bounds stay inside the lower quarter. Amber focus pixels and localized panel shading are checked, and rendered native live/VOD previews were inspected at both sizes. The fixture paints a reference frame beneath the actual controller; it does not decode video.

TC-APP-112 checks the retained rendered-surface/shutter state, bounds and play intent for live/VOD playing/paused fixtures through repeated native rewind/forward activation, actual settings popup opening/dismissal, Back and automatic hiding. TC-APP-113 waits for completed Compose recomposition before checking notice clearance and confirms that moving notices does not restart their original three-second timer. Existing focus/keys/settings/Live and capability cases remain passing. Initial row-bound and paused-fixture rendering failures were corrected before acceptance; there is no unresolved local test failure.

Fresh `test-smoke.ps1 -Lint`: **153/153 passed in 44.6 seconds**, zero failures/skips/errors (113 app and 40 environment/project/runner checks). Debug lint: **0 errors, 31 warnings**. Debug APK assembly passed. Requirement mappings and generated companion rows are current. No release build was needed.

No provider request, Firestick command, install, app start/stop or device setting change occurred. The painted frame, synthetic playback state and native previews establish local controller behavior, not hardware decoded moving-video acceptance. After separately approved deployment, real moving pictures and paused frames during repeated seeks, Live return, settings dismissal and TV readability still need the explicitly scoped viewing session described above. The panel can cover bottom subtitles while open, as recorded in the approved tradeoff.

## User deployment and basic physical acceptance — 24 October 2026

The user subsequently confirmed they deployed the update, that playback controls function and that the video is visible behind them. Installed/local APK identity was verified in the separately approved device session. This establishes basic physical picture/control acceptance; the broader individual paused-frame, long-pause/Live, settings/remote and readability variants were not separately confirmed by that report. The agent did not repeat the user's viewing checks. See [FIRESTICK_VALIDATION_2026_10_24.md](../investigations/FIRESTICK_VALIDATION_2026_10_24.md).

The subsequent direct-screen follow-up also verifies the native lower panel over moving video, 10-second seeks/feedback, a retained visible paused frame across 91.3 seconds, remote Live catch-up and native settings/Back dismissal with amber focus. Back then removed TvPlayerView from the actual activity hierarchy. An old accessibility browsing snapshot had incorrectly suggested playback exited; direct hierarchy/screenshots disproved that interpretation, so no exit fix was needed. Broader VOD/device/source variants remain separate; these observations are detailed in the same validation record.
