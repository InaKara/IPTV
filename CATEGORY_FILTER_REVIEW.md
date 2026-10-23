# Category headings and adult-category filter — CF-1

23 October 2026. **Proposed; awaiting explicit approval of UI-013 and SET-008 before implementation.** Existing uncommitted LV-1 implementation is retained. This revision authorizes no deployment or device actions.

## Requested outcomes and proposed details

| ID | Proposed behavior |
| --- | --- |
| UI-013 | The fixed top-left browser heading identifies the current view. Category roots show Live TV or VOD; opening a provider category shows its name, including Recently viewed. Favorites, Search and Settings show their destination names. Back, playback return, refresh and auxiliary return update the title to the actual restored view. |
| SET-008 | Add a remote-operable Settings switch labeled Filter adult content. Default to Filtering active, including existing installations without a saved value. Save one device-wide choice across launches and account changes, like grid density. While active, hide every category whose provider name contains adult, ignoring capitalization, and hide its entries throughout browsing. Turning it off restores them immediately from the loaded catalog. |

The user explicitly requested current-category headings and a default-active category-name filter. The following complete the proposed interaction and need approval with this revision:

- Use the selected category's displayed name rather than the generic Channels heading. For a blank category name, use Channels as the safe fallback. Keep the existing heading placement and styling; ellipsize long names on one line so they cannot overlap the grid or sidebar. Moving focus over another category does not change the title until it is opened.
- Use a case-insensitive **substring** match: Adult, ADULT and Adults all match. Match the provider category name only; channel names, URLs and other labels such as XXX are not inspected. This is a name filter, with no new provider classification or PIN mechanism.
- Apply visibility to Live TV, VOD, Recently viewed, Favorites, Search and the collection delivered to playback/channel stepping. Matching entries cannot reappear through these other browsing routes while active. An entry in a differently named category is evaluated by its own category, even if the stream also exists in a matching category.
- Keep the full cached playlist, canonical IDs, saved favorites and the shared 20-entry history. Filtering hides entries without deleting saved data or filling history with older replacements. Counts reflect visible entries, and Recently viewed remains available when empty. Turning filtering off restores the eligible saved entries without a provider request.
- Load the saved/default filter before first browser presentation, and keep it effective after refresh and account changes. Apply toggles immediately while retaining Settings focus. Closing Settings restores the previous visible item where possible; if its category is now hidden, return to the matching Live TV/VOD root with safe focus. Reconcile hidden/removed bookmarks and auxiliary return targets without an empty or stale playback launch.
- Keep whole-control amber remote focus and readable active/inactive status. Select toggles the switch; merely focusing it does not. Preserve density/account controls and their return navigation. No app restart is needed.

## Implementation approach and tradeoffs

Resolve the heading from the current browser location and category metadata. Keep one canonical catalog and derive visibility using the saved preference, instead of deleting categories during parsing or snapshot saving. This preserves stable identities and allows immediate reversal without downloading the playlist again. Reuse existing app-private preferences; no dependency or server request is needed. Device-wide persistence is the proposed choice because it keeps filtering consistent when switching accounts, as density already does.

Dependencies: UI-012/BROWSE-013 section roots and return rules; UI-007/009 fixed heading/grid geometry; SET-006/007 Settings and remote focus; BROWSE-010/011/012 bookmarks, Favorites and Search; RECENT-001–003 shared history; DATA-006/007 snapshots/refresh. On approval, reconcile their wording/test assertions only where this explicit filter and heading behavior changes it. Preserve unrelated approved behavior and pending audio scope.

## Validation and scope

Before approval, UI-013 and SET-008 have manual/pending coverage dispositions. Existing tests do not validate these requested changes. After approval, add meaningful local synthetic cases for heading transitions and long/blank names; mixed-case matching and nonmatching category names; default/saved persistence across reload/account changes; filtered counts/recents/Favorites/Search/playback collection; restoration without ID/data loss; refresh and safe focus/return when the previous category becomes hidden. Exercise the actual Settings switch and heading through Compose remote input, including empty lists and retained density/account navigation.

Run fresh local smoke with debug lint and build the debug APK after implementation. Physical TV readability, complete Activity lifecycle and actual playback remain separate acceptance. No release build, commit/push, provider request, deployment or app start is included.
