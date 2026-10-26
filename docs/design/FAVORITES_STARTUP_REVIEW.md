# FS-1 — start fresh browsing in Favorites

26 October 2026. **Explicitly approved with Approve Favorites implementation first after the concrete revision/in-chat overview and deployment clarification.** BROWSE-019 and the startup-only UI-012/BROWSE-013/BROWSE-011 exceptions below are selected for local implementation/tests. User request: make the app start in Favorites instead of Live TV. Deployment, device/provider actions, commit and push remain separately authorized operations.

## Pre-FS-1 behavior

BrowserState and BrowserController reset started in Live TV with sidebar focus on its screen icon. HomeViewModel loaded account preferences/favorites before publishing the first successful browse state and reset the controller on account change. Favorites was an auxiliary view whose Back target defaulted to Live TV; changing only the initial destination would retain that hidden return path. Reused MainActivity/ViewModel sessions already preserved their current destination; FS-1 retains that EX-1 routing behavior.

## Approved scope

| ID | Approved change |
| --- | --- |
| BROWSE-019 — new | A fresh browser session, including a new process/controller or account reset, opens Favorites with the heart selected and initially focused after account/list preparation. This is a fixed startup default, not a new preference or remembered last-used destination. |
| UI-012 — startup clause only | Replace fresh Live TV/screen focus with Favorites/heart focus. Keep sidebar order Live TV, VOD, Favorites, Search and bottom Settings, plus all existing focus/activation rules. |
| BROWSE-013 — startup clause only | Replace the fresh Live TV default; preserve Live TV/VOD roots, categories, identities, filtering, independent bookmarks and normal returns. |
| BROWSE-011 — initial-Favorites Back exception | When Favorites is the initial view and no preceding section exists, Back uses the existing root Cancel/Exit confirmation under BROWSE-008. When Favorites is opened from an existing Live TV/VOD context, Back retains its prior section/category/item/scroll return. Repeated heart activation must not invent or overwrite a return target. |

Keep the initial Favorites view even when it contains no available visible items: use the existing empty state and usable heart/sidebar focus, with no automatic switch to Live TV or unfiltered items. Retain current account-scoped favorites, canonical identities, ordering, adult filtering, cached API-only playable/season references and existing demand-driven resolution/loading. Do not migrate, delete or refavorite anything and do not add eager metadata/provider requests to startup.

Bringing an existing session to the foreground, the EX-1 reused-browser handoff, configuration recreation with its retained ViewModel and return from playback/Search/Settings keep the existing destination/item/focus/scroll rather than jumping to the startup default. A genuinely new session after process loss uses Favorites. Successful account replacement also initializes that account in Favorites; failed account edits keep the working session as before.

The explicit user request is the Favorites startup section. Initial heart focus, staying in empty Favorites, root Back with no invented predecessor and preserving resumed sessions were reviewed associated choices and included in the selected approval. Sidebar reordering, a startup-section Settings control, automatic playback, cache/account changes and changing every foreground return are excluded.

## Implementation approach and checks

Initialize the new browse session and its focus/return state coherently before the first browse render; do not simulate a sidebar click after a Live TV frame or force navigation on every onResume. Preserve the section/category Live TV fallback where it is still meaningful, rather than globally changing every default BrowserLocation used by unrelated return paths. Keep an explicit distinction between initial Favorites without a predecessor and Favorites opened from another section. No database/schema or dependency change is needed.

After approval, add meaningful controller/Compose regressions for fresh populated/empty/adult-filtered Favorites, initial heart focus, account reset, deferred available library references, root Cancel/Exit, ordinary Favorites Back and nested playback/Search/Settings returns. Revise existing Live-TV-default assertions and make other tests open the section they intend to exercise. Retain useful assertions instead of weakening them. Review affected mappings, run fresh smoke/lint and debug assembly. Actual Fire TV startup/remote/Exit acceptance remains manual after separately approved deployment/device use.

## Pre-implementation catalog review

BROWSE-019 was Open with a pending Manual disposition. Existing UI-012/BROWSE-013/BROWSE-011 behavior remained effective until selected FS-1 approval, without renumbering or treating baseline status as approval. Fresh test-smoke.ps1 passed 207/207 existing checks (167 app plus 40 shared), zero failures/skips/errors, in 36.7 seconds after mapping/generated-row maintenance. That was baseline/catalog evidence before implementation, not proof of Favorites-first startup. Project-contract installation output was mocked, with no device/provider access.

## Local implementation — 26 October 2026

The initial BrowserState and reset now contain Favorites and its sidebar/heart bookmark before catalog publication. BrowserLocation's ordinary Live TV default remains available for unrelated fallback routes. Favorites has an explicit nullable predecessor: initial Favorites with none is a root, while visits from Live TV/VOD keep the real return location. Nested Search/Settings and temporary library-detail returns preserve the initial no-predecessor state. A startup-only blank heart bookmark does not replace normal first-visit grid focus when Favorites is later opened from a section.

HomeViewModel's existing account-owned preference/catalog load, account reset, deferred local reference resolution and retained ViewModel lifetime remain; there is no post-render click, onResume redirect, new stored setting, provider operation, schema/data migration or library dependency change.

TC-APP-168/169 verify canonical/filtered/unavailable/late favorite references, initial versus ordinary Back targets, nested/library handoffs, reload bookmarks and reset. TC-APP-170/171 use actual Compose first-layout/remote input for saved, empty and filtered Favorites, heart selection, playback/item restoration, auxiliary returns and reversible filtering. TC-APP-172 verifies actual account preferences/local SQLite references, first-layout reconciliation gating, zero provider attempts, retained reload view and separate account reset/retention. TC-APP-164 now exercises the actual root Cancel/Exit dialog from initial Favorites with task/data safety. Existing Live-context tests explicitly select Live; startup-flow timing tests now identify Favorites as the real first destination.

The first focused run caught initial heart focus leaking into an ordinary Favorites visit; its startup-only bookmark handling was corrected. Fixtures were aligned with the existing Favorites empty wording, saved sidebar-focus return and approved Favorites account-reset behavior, retaining their visibility/navigation assertions. All 28 focused checks passed before the final provider-attempt count assertion was added. Complete physical Activity/process/window/relaunch/remote/Exit acceptance remains manual; no Firestick action, deployment, provider access, commit or push occurred.

Fresh test-smoke.ps1 -Lint passed **212/212** (172 app plus 40 shared), zero failures/skips/errors, in **41.3 seconds**, including the final provider-attempt assertion. Lint has zero errors/33 warnings and debug assembly passed separately. After correcting MC-UI-012's superseded Live-TV-default wording, final fresh catalog maintenance passed **212/212 in 37.3 seconds**, zero failures/skips/errors, within the warm target. Definitions, reviewed mappings and generated companion rows are current. These results establish local implementation checks, not installation or physical TV acceptance.
