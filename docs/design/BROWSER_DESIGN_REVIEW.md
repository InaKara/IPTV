# Browser redesign: review revision BR-1

Prepared 22 October 2026. **BR-1 explicitly approved: “approved, implement”, 22 October 2026. Local implementation completed; physical Fire TV acceptance pending.** The user's answers below settle the identified product questions. Approval covers the complete revision and selected local implementation/test/documentation scope; publication and deployment remain separate.

**Later approved revision:** [LV-1](LIVE_VOD_BROWSER_REVIEW.md), 23 October 2026, extends the sidebar to Live TV, VOD, Favorites, Search and bottom Settings. It separates the former combined category root and changes fresh-session focus to the Live TV screen icon. The historical BR-1 design below remains context; LV-1 takes precedence for these revised behaviors.

[CF-1](CATEGORY_FILTER_REVIEW.md), also approved on 23 October, replaces generic Channels headings with the current category/destination and adds the default-active saved adult-category filter in Settings. It takes precedence over the historical combined-list/Favorites heading and unfiltered visibility descriptions below.

The reference is the user-supplied **Amber-focused IPTV channel grid.png** and accompanying design prompt. The later answers take precedence over the reference and prompt. The image guides real native components; its sample channel artwork and content are not production data.

## Decisions from the user

| Question | Decision recorded on 22 October 2026 |
| --- | --- |
| Playback while browsing | Do not continue playback when leaving the player. Preserve resource release. |
| Programme data | Do not add programme-data features to this revision. |
| Managing Favorites | Use the remote Menu button on the focused channel to offer Add to Favorites or Remove from Favorites. |
| Search and Settings | Plan a minimal first implementation. Proposed details appear below for review. |
| Recently viewed and category order | Keep Recently viewed first, then TR categories and the existing alphabetical ordering. |
| Channel numbering | Use provider numbers only for now. |
| Back from Favorites | The user delegated the initial choice. Chosen behavior: restore the previous Categories or category-channel view, including focus and scroll. This can be reconsidered later. |

Because playback stops on return to browsing, the browser has no PLAYING badge or Back-to-ongoing-playback action. At Categories root, retain the current exit-confirmation dialog. A previously watched channel must not be labeled as still playing.

## Approved requirement revision

These are the approved BR-1 requirements. Current implementation status, generated results and remaining acceptance gaps are recorded in [REQUIREMENTS.md](../requirements/REQUIREMENTS.md).

| ID | Proposed first-version outcome |
| --- | --- |
| UI-007 | Reference-based dark browser shell and persistent four-icon sidebar. |
| UI-008 | Category/channel tile variants and full amber focus treatment, separate from active destination. |
| UI-009 | Precisely fitted, vertically scrolling grid with three density presets. |
| BROWSE-009 | Categories-to-Channels hierarchy and context-sensitive Back. |
| BROWSE-010 | Deterministic remote/keyboard movement and stable focus/scroll restoration. |
| BROWSE-011 | Persistent Favorites, Menu action and the delegated Back behavior. |
| BROWSE-012 | Minimal channel-name search on a separate screen. |
| SET-006 | Minimal Settings screen containing the saved grid-density choice. |
| DATA-005 | Optional provider channel number, without generated numbering. |

## Appearance and tile contents

Use the existing Kotlin/Compose for TV app, dark branding and channel-logo loader. Remove the old Home/category-list presentation from the browsing UI. Show only a heading and grid in the main browser area. Do not introduce top tabs, top Favorites/All channels/Search controls, banners, previews, thumbnails or decorative panels. Startup and launcher artwork remain as they are.

The persistent sidebar contains outline icons with accessible names: Categories (monitor), Favorites (heart), Search (magnifying glass), and Settings (gear anchored near the bottom). Focusing an icon does not open it. Activate with OK/Enter or click.

- Category tile: centered category symbol or available representative asset, prominent category name, secondary actual channel count. Use a neutral category symbol when no representative asset exists; do not fetch or invent category artwork.
- Channel tile: centered existing channel logo/fallback, then the optional provider number and channel name on one line. If no provider number exists, show the name alone. Preserve the reference tile dimensions and generous space rather than adding replacement content.
- Each tile is exactly one focusable control. Use single-line ellipses for long labels, readable text and undistorted logos.
- Exactly one control is focused. Fill focused tiles and sidebar buttons with warm amber and use dark text/outline icons. Preserve channel artwork colors. Do not scale, glow, shift neighboring content or rely on a border alone.
- An active sidebar destination without focus has a quiet charcoal background and a short white left-edge indicator. Categories and its channel level activate the monitor; Favorites activates the heart. Focus and active destination are independent.
- Empty categories/Favorites show a plain empty-state message with usable sidebar focus. Do not add filler tiles. Empty-state text is the necessary exception to heading-plus-grid content.

Reference geometry is a logical 1672 x 941 canvas, scaled proportionally to the available television viewport. It is not a fixed Android pixel/dp canvas:

| Element | Reference units |
| --- | --- |
| Sidebar width | Approximately 120 |
| Main content / right margin | Starts at x=152 / approximately 24 |
| Heading / grid origin | Approximately (152,40) / (152,104) |
| Grid viewport | Approximately 1496 x 816 |
| Horizontal and vertical gaps | 12 |
| Standard tile | Approximately 290 x 195 |
| Tile corner radius / horizontal padding | 8 / 18 |
| Sidebar control / icon | Approximately 84 x 84 / 36 x 36 |
| Heading / channel-label font size | Approximately 40 / 18 |

Colors: page `#101314`, sidebar `#0C0F10`, tile `#191D1E`, border `#363C3E`, primary text `#F5F5F3`, secondary text `#D4D8D9`, amber focus `#FFD15C`, focused text/icons `#111512`. Use flat surfaces and a clean sans-serif font.

Standard density is five equal columns and four fully visible rows (20 real tiles when available). Large is 4 x 3; Compact is 6 x 4. Compute tile dimensions from the viewport and gaps, with no clipped bottom row. Additional rows scroll while the heading and sidebar stay fixed. Fewer items occupy only their real positions.

## Categories, navigation and playback

After the existing login/loading flow, show **Categories** with category tiles, including Recently viewed first even when empty. Keep existing history persistence/20-entry limit and TR-first/alphabetical category ordering. Initial focus goes to the first category tile; if there is none, keep focus in the sidebar.

Activating a category replaces the shared grid's data/tile variant and changes the heading to **Channels**. Keep its stable category ID in application state without a breadcrumb or extra category heading. Returning to an already visited category restores that category's own saved focus and scroll. Activating the monitor explicitly returns to Categories; it does not reset the category-list position.

Arrows and D-pad share these rules:

- Left/Right move one column and Up/Down one row. Do not wrap across rows or beyond the collection's boundaries; if no item exists in the target column of a partial final row, keep focus in place.
- Left from the first column moves to the active sidebar destination. Right from the sidebar restores the current grid's last focused item, or its first item on an initial visit. An empty grid leaves usable focus in the sidebar.
- Sidebar Up/Down visit all four icons, including the bottom Settings icon, and stop at the ends.
- Scroll an offscreen target into view, wait for its layout and then focus it. The heading and tile contents are not additional focus targets.
- Movement never changes category/destination or starts playback. OK/Enter/click activates the focused control.

Keep independent focus and scroll state for Categories, each category's channels, Favorites and search results. Use stable category/channel identities, not only indices; duplicate or missing provider identifiers must not merge distinct tiles. On membership changes, retain the focused ID when it still exists, otherwise use a valid nearby item or sidebar fallback. Preserve session state through activity recreation without storing playlists or private URLs in saved-instance bundles; retain the existing process-death/login behavior.

| Starting view / action | Result |
| --- | --- |
| Category's Channels / Back | Categories, with the prior category tile and scroll restored. |
| Categories root / Back | Existing Cancel/Exit confirmation dialog. |
| Favorites / Back | Restore the last Categories or category-channel view saved when Favorites was opened. Default to Categories if no prior view exists. Re-activating the heart must not overwrite that return target. |
| Search or Settings / Back | Restore the view that opened it, including Favorites if applicable, and its focus/scroll. Dismiss an open search keyboard before leaving Search. |
| Channel activation in category/Favorites/search | Open the existing full-screen player using that collection as its channel-switching context. |
| Player / Back | Stop/release playback and restore the originating view, focused channel and scroll. From Favorites, a further Back follows the Favorites rule above. |

Preserve existing player controls and VOD behavior. PLAY-003 channel direction and VOD-004 Stop semantics are separate unresolved requests. Changing browser focus/density/category does not start a stream. Playback is never retained underneath the browser or these auxiliary screens.

## Minimal Favorites, Search and Settings

Favorites: the heart opens a direct channel grid headed **Channels**. On a focused channel in category, Favorites or search results, Menu opens a small action with Add to Favorites or Remove from Favorites. Keyboard Context Menu/Shift+F10 opens the same action. Dismissing the action restores focus without playback. Keep one focus target per channel tile. Save favorites across restarts, scoped to the configured account, using references resolved against the current playlist. Unavailable entries are not rendered as fake channels; retain their saved references. Show available favorites in their original playlist order. These persistence/order defaults were included in the approved BR-1 revision.

Search: a separate screen headed **Search**, with one remote-editable query field and channel results using the channel tile style. Match case-insensitive substrings of names across the currently loaded playlist, preserving playlist order. An empty query shows a short instruction, without a manufactured All channels page; a nonmatching query shows an empty-state message. Results update from cached data without a new provider request. Opening Search focuses its field without forcing the keyboard open; OK/Enter requests editing. Result activation opens playback, and Menu provides the same favorite action. Preserve query/results/focus while returning from playback. No category filters, suggestions or new search service in this first version.

Settings: a separate screen headed **Settings**, with only the three grid-density choices, Standard selected by default. Save the selected choice across app launches using the existing local preference mechanism, apply it to all browser grids and search results, and restore focus by stable ID after reflow. Do not add account editing, themes, startup/cache policy or unrelated controls.

This records BR-1's approved boundary. The later [SC-1 proposal](STARTUP_REFRESH_REVIEW.md) would expand Settings with account/refresh controls and automatic cached startup. It awaits review and does not retroactively change BR-1's approval or claim implementation of these new controls.

Provider numbers: read the optional playlist `tvg-chno` value, trimming whitespace and treating blank/missing values as absent. Display it as supplied rather than renumbering, synthesizing a value from `tvg-id`/array position, or changing playlist order. Support for other provider-specific numbering fields would need a sample/requirement revision; this proposal does not assume the real provider supplies numbers. Verify with synthetic local playlists.

## Implementation approach and tradeoffs

Reuse Compose for TV, ViewModels/StateFlow, the loaded M3U repository, Media3, Coil's existing raster/SVG handling and app-private preferences. One native browser shell and reusable lazy grid serve both levels and Favorites. This preserves the current toolchain and avoids a framework or navigation-library migration. Explicit focus/scroll state is more work than relying on default spatial focus, but it is necessary for the requested remote boundaries and restoration.

Filter/cache the already loaded category/channel data off the main thread as appropriate; retain existing logo caches. The minimal Favorites/Settings persistence can use current storage conventions rather than introducing a database. That is adequate for this first scope, with no new cross-device sync or background service. Preserve account separation and keep authenticated references out of public logs and saved-instance state. The tradeoff of stopping playback is that the browser cannot display a truthful currently-playing badge or return directly to a still-running stream.

## Validation and approval boundary

The original draft used manual Not run dispositions. Implementation adds meaningful local parser, state, persistence, Compose navigation and native-rendering checks, mapped individually in the catalog. Physical-device cases remain Not run by the host suite. Reuse synthetic/local logos, channels and streams; do not probe live external channel logos or a real provider. Retire obsolete navigation assertions only when their replacement is implemented, keeping stable testcase IDs where their intended behavior remains valid.

Validate populated/empty/scrolled lists, incomplete final rows, duplicate identifiers, category/Favorites/search playback return, every sidebar button and density changes. Visual checks must confirm 20 Standard tiles fit and amber sidebar focus is as obvious as grid focus at TV resolutions. Keep automated/build evidence distinct from physical remote, keyboard, image fidelity and actual playback/resource-release acceptance. Run the required fresh smoke command after mapping changes and after implementation; use relevant build/lint checks for the implemented UI. Physical-device installation/acceptance is a separate authorized step.

The approved implementation scope is **UI-007-009, BROWSE-009-012, SET-006 and DATA-005**, plus the corresponding existing requirement statuses/mappings that need reconciliation. BROWSE-002's text category sidebar and BROWSE-004/005's old focus targets are replaced by UI-007/BROWSE-010; BROWSE-006's historical Back wish is fulfilled by BROWSE-009. The old IDs/history are preserved with replacement references and reviewed testcase mappings.

The user explicitly approved this complete revision with “approved, implement” on 22 October 2026, authorizing local app changes, relevant implementation tests and accompanying documentation. Commits, pushes, deployment and LOGIN-008's startup/cache behavior remain outside this scope. The seven clarification answers and the later implementation approval are distinct review records.
