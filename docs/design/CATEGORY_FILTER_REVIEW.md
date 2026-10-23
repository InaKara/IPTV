# Category headings and adult-category filter — CF-1

23 October 2026. **Approved and locally implemented: the user explicitly replied approved, implement for UI-013 and SET-008, including the documented defaults, matching, persistence and return rules. Physical device acceptance remains pending.** Existing LV-1 behavior is retained. This revision authorizes no deployment or device actions.

## Approved outcomes and details

| ID | Approved behavior |
| --- | --- |
| UI-013 | The fixed top-left browser heading identifies the current view. Category roots show Live TV or VOD; opening a provider category shows its name, including Recently viewed. Favorites, Search and Settings show their destination names. Back, playback return, refresh and auxiliary return update the title to the actual restored view. |
| SET-008 | Add a remote-operable Settings switch labeled Filter adult content. Default to Filtering active, including existing installations without a saved value. Save one device-wide choice across launches and account changes, like grid density. While active, hide every category whose provider name contains adult, ignoring capitalization, and hide its entries throughout browsing. Turning it off restores them immediately from the loaded catalog. |

The user explicitly requested current-category headings and a default-active category-name filter. The following complete the approved interaction:

- Use the selected category's displayed name rather than the generic Channels heading. For a blank category name, use Channels as the safe fallback. Keep the existing heading placement and styling; ellipsize long names on one line so they cannot overlap the grid or sidebar. Moving focus over another category does not change the title until it is opened.
- Use a case-insensitive **substring** match: Adult, ADULT and Adults all match. Match the provider category name only; channel names, URLs and other labels such as XXX are not inspected. This is a name filter, with no new provider classification or PIN mechanism.
- Apply visibility to Live TV, VOD, Recently viewed, Favorites, Search and the collection delivered to playback/channel stepping. Matching entries cannot reappear through these other browsing routes while active. An entry in a differently named category is evaluated by its own category, even if the stream also exists in a matching category.
- Keep the full cached playlist, canonical IDs, saved favorites and the shared 20-entry history. Filtering hides entries without deleting saved data or filling history with older replacements. Counts reflect visible entries, and Recently viewed remains available when empty. Turning filtering off restores the eligible saved entries without a provider request.
- Load the saved/default filter before first browser presentation, and keep it effective after refresh and account changes. Apply toggles immediately while retaining Settings focus. Closing Settings restores the previous visible item where possible; if its category is now hidden, return to the matching Live TV/VOD root with safe focus. Reconcile hidden/removed bookmarks and auxiliary return targets without an empty or stale playback launch.
- Keep whole-control amber remote focus and readable active/inactive status. Select toggles the switch; merely focusing it does not. Preserve density/account controls and their return navigation. No app restart is needed.

## Implementation approach and tradeoffs

Resolve the heading from the current browser location and category metadata. Keep one canonical catalog and derive visibility using the saved preference, instead of deleting categories during parsing or snapshot saving. This preserves stable identities and allows immediate reversal without downloading the playlist again. Reuse existing app-private preferences; no dependency or server request is needed. Device-wide persistence keeps filtering consistent when switching accounts, as density already does. Recent references resolve matching category/user-agent metadata when a stream is present in multiple categories, so an entry is evaluated by its own category. Only duplicate streams need an additional lookup list; canonical IDs and snapshot/history schemas stay unchanged.

Dependencies: UI-012/BROWSE-013 section roots and return rules; UI-007/009 fixed heading/grid geometry; SET-006/007 Settings and remote focus; BROWSE-010/011/012 bookmarks, Favorites and Search; RECENT-001–003 shared history; DATA-006/007 snapshots/refresh. BROWSE-011/012/013 and SET-006 wording and meaningful test mappings are reconciled for this explicit filter and heading behavior. Unrelated approved behavior and pending audio scope are preserved.

## Validation and scope

Six new cases TC-APP-094–099 cover heading transitions, actual native long-title ellipsis/bounds and blank fallback; mixed-case matching and nonmatching channel names/URLs/category labels; saved/default persistence and account reload; filtered counts/recents/Favorites/Search including late search results; reversible identities; refresh/reset and nested returns when the previous category becomes hidden; actual remote Settings switch focus and filtered playback delivery. Existing native all-density layout assertions now also verify the switch's amber focus pixels, visible bounds and toggle focus. The existing density/account navigation case remains passing.

Final validation: fresh `.\test-smoke.ps1 -Lint` passed **139/139** in **32.01 seconds**, with zero failures/skips/errors. Debug lint has **zero errors and 30 warnings**. Debug APK assembly succeeded. The local native Settings preview was visually inspected; this is a synthetic fixture, not a Firestick screenshot. Initial UI-test failures were corrected by using native text metrics for ellipsis and traversing the actual existing Settings entry path; the full suite then passed. No tooling/dependency/build configuration changes or lint suppressions were introduced.

Physical TV readability, complete Activity lifecycle and actual playback remain separate acceptance. No release build, commit/push, provider request, deployment or app start was performed for CF-1.

Historical proposal-stage validation: fresh local smoke passed **133/133**, with zero failures/skips/errors, on 23 October 2026. At that point the two new manual cases remained Not run and app/test implementation had not started. The initial restricted run could not write the Gradle cache; missing documentation-source fields on the pending manual mappings were corrected before that successful proposal-stage run. Current implementation evidence is recorded above and generated in docs/requirements/REQUIREMENTS.md/TEST_CASES.md.
