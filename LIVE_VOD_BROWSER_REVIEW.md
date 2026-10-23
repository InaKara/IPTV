# Live TV and VOD sidebar — LV-1

23 October 2026. **Approved and locally implemented: the user explicitly replied implement, selecting UI-012 and BROWSE-013 including all documented defaults and return rules. Physical Fire TV acceptance remains pending.** The user asks for a dedicated VOD categories icon, with the computer screen as the default/top selection, followed by VOD, Favorites and Search. Local implementation, meaningful tests and documentation are authorized. Deployment and device actions are not.

## Sidebar and default view

| Position | Icon / destination | Behavior |
| --- | --- | --- |
| 1 / default | Existing computer screen / Live TV | Open Live TV categories. |
| 2 | Outline film icon / VOD | Open VOD categories. |
| 3 | Existing heart / Favorites | Open shared favorites from both sections. |
| 4 | Existing magnifier / Search | Search names across both sections. |
| Bottom | Existing gear / Settings | Keep account/load/refresh and density controls. |

This means **four main icons plus bottom Settings: five controls in total**. Retaining Settings is the approved interpretation of the requested main-icon order. Use the existing sidebar width, button/icon sizes, dark theme, amber focus and active indicator, with no new tabs, banners or screens beside the requested VOD category root. Draw the film icon in the existing native icon system; no new image or icon dependency is needed.

Approved startup detail: a fresh browser session opens Live TV categories and initially focuses the screen icon. Returning from playback or an auxiliary view restores its saved destination/item, rather than resetting to the default. This initial focus supersedes BR-1's first-category focus. Category-root headings become **Live TV** and **VOD**; an opened category keeps the existing **Channels** heading/tile presentation.

Moving focus never activates a destination. Up/Down visit all five controls without wrapping. Right restores the last grid item for the current view; Left from the first grid column focuses the matching active icon. Empty views retain usable sidebar focus. All three density layouts keep the existing viewport and tile geometry.

## What goes in each section

Reuse the current cached playlist and its existing `isVod` flag. VOD contains entries classified true; Live TV contains entries classified false. Provider `group-title` values remain the category names. A category with both types appears in both sections, each with its own matching entries/count. Entries are not duplicated within a section or silently omitted; regular categories with no matching entries are absent from that section.

The current parser guesses VOD from category words, URL paths and file extensions; all other entries fall back to live. **The sidebar split does not make those guesses provider-verified.** Do not introduce new classification heuristics, a third Unknown destination, account probes or a structured provider API migration under this revision. If more reliable classification is desired later, review it separately against actual metadata.

Keep Recently viewed first in both category roots, including when empty. Filter the existing shared maximum-20 history by the selected type rather than creating two new history stores/limits. Keep TR-first/alphabetical ordering for remaining categories, and preserve each matching category's channel order from the playlist.

Favorites and Search continue across both types, with existing account scoping, persistence, filtering and ordering. Creating the two browser sections must not change stable channel IDs or invalidate saved favorites/history. Refresh/snapshot behavior and account isolation remain intact. Movies and series recognized by the existing classifier share VOD; movie-detail, season/episode and programme-guide features are not added.

## Navigation and return behavior

| Action | Approved result |
| --- | --- |
| Activate screen / film icon | Its own category root, preserving that root's focus/scroll history. |
| Open a category | Only that section's matching entries; preserve the provider category identity plus section in the view key. |
| Back from category entries | Matching Live TV or VOD root with its category tile restored. |
| Back at either category root | Existing Cancel/Exit confirmation. |
| Open a channel/item | Existing player, with channel stepping confined to the selected filtered collection. |
| Return from playback | Restore the originating Live TV/VOD/category/Favorites/Search view and item. |
| Back from Favorites | Restore the preceding Live TV/VOD root/category; default to Live TV when no return target exists. Reopening Favorites does not overwrite the target. |
| Back from Search/Settings | Restore the opening view and section, including Favorites when applicable; preserve existing keyboard dismissal rules. |

Store independent bookmarks for both roots and every section/category combination, even when the category has the same provider name. Preserve stable item identities and valid fallback focus through refresh, changed membership, empty lists and retained Activity recreation. This must not create new private URLs in Android saved-instance bundles or start playback in the browser.

## Approach and tradeoffs

Extend the existing native Compose browser/controller over one shared catalog. Filtering the current `isVod` entries is a small data/view-state extension and requires no provider request or additional service, framework or dependency. Stable entries must be indexed once and reused across section views so that favorites and duplicate handling retain their identities. The main work is correctly separating bookmarks, roots, return targets and remote focus, rather than merely drawing an extra icon.

An explicit provider API could improve content-type confidence, but would add provider compatibility, network/cache/schema and failure behavior outside this request. Reusing the existing classifier is the approved approach; its limitations remain documented.

UI-012/BROWSE-013 coordinate with UI-007/008/009, BROWSE-003/009/010/011/012, VOD-001, existing history, cache/refresh and Settings requirements. Existing catalog IDs and historical evidence remain stable. Conflicting four-icon/combined-root wording and meaningful existing test assertions have been reconciled as part of this selected revision.

## Validation

The normal fresh local smoke command regenerates catalog results after the mapping review; it does not access the Firestick or provider.

Historical review-stage validation on 23 October 2026: fresh local smoke passed **128/128** in 28.2 seconds. That run covered the preceding implementation; the proposed LV-1 behavior was still pending at that point.

Final local implementation validation on 23 October 2026: fresh `.\test-smoke.ps1 -Lint` passed **133/133** in **35.32 seconds**, with zero failures/skips/errors. Five new cases TC-APP-089–093 cover synthetic mixed category membership/count/order, canonical identity/history/global Favorites/Search, independent bookmarks/Back/nested returns, refresh/type changes/account reset and actual Compose five-control/default/empty/scrolled playback-return behavior. Native all-density rendering checks also cover amber focus on all five controls. Canonical entries are partitioned once without changing snapshot schemas or persisted IDs; reset clears the previous catalog before a new account loads.

Debug assembly, optimized unsigned-release assembly and release lint passed locally. Debug/release lint reported zero errors and 30/34 warnings; no tooling upgrades or check suppression were introduced. The release build was an extra packaging check. For the user's current personal Firestick workflow, future routine verification uses the debug APK and relevant local checks; a release build has no immediate deployment benefit.

Physical remote/readability, full Activity recreation and actual playback acceptance remain separate from these host checks. No commit/push, deployment, app start or provider/device investigation was performed for LV-1. AU-1 software audio decoding remains separately unapproved.
