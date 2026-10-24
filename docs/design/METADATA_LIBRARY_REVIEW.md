# Metadata uses — MD-1 proposal

24 October 2026. The user selected the recommended metadata uses with “Ok, proceed with your recommended uses for metadata”. This document makes the visible behavior, storage choice and defaults concrete for the required revision review. It is a proposal, not implementation/deployment approval. No app implementation or implementation tests are added while review is pending.

## Selected outcomes and delivery order

| Stage | Requirements | Proposed outcome |
| --- | --- | --- |
| 1 | DATA-008, LIB-002 | Provider movie/series library, details and season/episode navigation, fetched on demand. |
| 2 | VOD-006 | Account-scoped watched state, Resume/Restart and Continue watching. |
| 3 | BROWSE-015, PLAY-014 | Useful metadata sorting/unwatched filtering and a compact live playback Info panel. |

All three stages are proposed for selected local implementation in that order. LIB-001 and BROWSE-014 remain broader umbrella backlog items; MD-1 delivers the provider-library and metadata-sorting subsets, not local/network files or every possible filter. EPG-001 stays Deferred. Faster startup remains a separate backlog request.

## What the provider actually supplies

The saved 23 October API responses contain about 19,566 movies and 3,859 series. Movie listings supply names, categories, posters, ratings, added timestamps and playback IDs/formats. Movie details additionally supply synopsis, cast/director, genre, release date, duration and backdrop artwork. Some have external IDs/trailer links. Series listings supply summaries and seasons/episodes are obtained by opening a series detail response; that response can contain all seasons/episodes for the selected series, not one independently requested season.

Missing fields are normal. Only 13,159 movie ratings were positive; 6,254 were zero and 153 were missing/unparseable. Movie listing responses lack a release-year/genre field, so complete movie year/genre filters cannot be promised from the lightweight index. Live API entries expose adult and archive indicators, but flag accuracy has not been audited and none advertised archive support. Programme mapping is sparse. No fresh provider request was needed to prepare this proposal.

## Stage 1: browsing and details

Keep the five sidebar controls in their existing order, with Live TV as the fresh-session default. Live TV continues using the current playlist browser and startup path.

Proposed VOD root cards: **Movies**, **Series**, **Continue watching** (stage 2), **Recently viewed** and **Playlist categories**. Recently viewed keeps the existing shared 20-entry history; Continue watching is separate persistent progress. Playlist categories opens the complete existing VOD category browser as a compatibility/failure fallback. This explicitly revises BROWSE-013's primary VOD root; its Live TV, legacy playlist and shared return/identity behavior remain.

- Movies opens provider movie categories, with an All movies entry. A category opens its title grid. Selecting a movie opens details with its name, available poster, synopsis, release year, duration, rating, genre and cast/director. Primary action is Play initially, or Resume under stage 2; Restart and favorite/context actions remain available as appropriate. Missing fields are omitted; missing artwork keeps a neutral fallback. Unknown metadata does not prevent playback.
- Series opens provider series categories and All series. Selecting a series opens its description, seasons and episode lists. Episodes show provider season/episode labels, title, available runtime and progress. Episode selection offers Play/Resume and Restart. Do not invent episode numbers when metadata is missing. A next-episode action may be offered after completion; no automatic next-episode playback in MD-1.
- Headings identify the opened library, category, title, series or season. Long text stays bounded; focused controls retain the existing whole-control amber style. Metadata details use a dedicated content view within the existing sidebar shell, rather than changing the normal channel grid into a poster/banner layout. Existing grid densities remain usable.
- Back unwinds details/episodes/seasons/category/library one level at a time; root Back retains exit confirmation. Player return restores the exact item, page and scroll. Section activation and Favorites/Search/Settings returns preserve the matching library/playlist location. Empty/error/offline views keep sidebar/Back and a Retry or Playlist categories route usable.

### Identity and existing favorites/history

M3U snapshots, values and existing favorite/history identities are retained. Metadata does not replace account settings, saved channel numbers or the entire playlist. Link API movies/episodes to a legacy playable entry only through an unambiguous configured-provider/account/type/ID route match, preserving that entry's existing URL, user-agent, category and duplicate occurrence. Do not merge by display name or change old favorite keys to make a match. Ambiguous entries remain accessible through the playlist fallback.

API-only playable movies/episodes use a typed provider/account identity, distinct from series containers and other stream types with the same numeric ID. Additive identity links let the same unambiguously matched playable item share favorites/progress across routes. Existing favorites and history survive refresh, password rotation and unavailable metadata. Series containers are navigational, not playable favorites; playable episodes can be favorites.

Global Favorites/Search retain all current playlist items. They can also resolve downloaded API movie/series index entries and cached episodes, deduplicating unambiguous playable matches. Search can open a series details view; it must not fetch every series' episodes to answer a query. Unknown/uncached content is not falsely presented as fully indexed. New API-only playback can participate in the shared bounded recent history without discarding legacy references; unresolved entries remain retained for later resolution.

### Existing adult filter

Preserve SET-008's saved category-name substring rule, active by default, across the new lists, details, Continue watching, favorites/search and delivered playback collections. API-only items use their primary provider category; legacy matched occurrences keep their existing category semantics. Do not broaden filtering to the API adult flag in MD-1: stronger flag-based filtering needs a separate accuracy/behavior review. Retain hidden entries and progress; disabling the filter restores eligible content without data loss.

## Stage 2: watching memory defaults

Progress is device-local and account-scoped, not a server-provided watch history or cloud/profile feature. Use stable typed provider IDs when available and a preserved legacy identity for playlist-only VOD. Do not fuzzily transfer progress to a different movie/episode after a refresh. Preserve account history through password changes; cancel late writes from another active account. Do not clear progress when metadata is evicted or temporarily unavailable.

Proposed defaults for review:

- Save actual position/duration every 10 seconds while playing and on Pause, Back, background, channel change and end. Do not track live-TV progress. Resume requires an actually seekable VOD timeline; clamp restored position to its valid bounds. Unknown/nonseekable timelines keep usable Play and do not invent a resume capability.
- Positions below 30 seconds start from the beginning and do not appear in Continue watching. Unfinished eligible items show Resume as the first focused details action, with Restart available; selecting a title never silently starts playback before that action.
- Mark watched at 95% of a known positive duration, or on a genuine ended event. Marking watched does not stop playback or skip credits. Watched items leave Continue watching; their primary details action is Play from start. Restart resets that item's watched/progress state for a new viewing attempt.
- Menu offers Mark watched and Mark unwatched/reset for a playable movie/episode. Mark unwatched resets position to zero. Manual watched/reset actions work even when automatic resume is unavailable. Do not silently mark an entire unloaded series watched.
- Continue watching is ordered by last viewing time, with one most recently unfinished episode per series plus unfinished movies, and paged from disk. It does not change the shared 20-item Recently viewed limit. Progress for unavailable/filtered titles is retained and hidden until eligible again. No automatic next-episode playback or new Stop semantics are selected.

Tradeoff: the 30-second threshold avoids filling Continue watching with brief samples; 95% is an approximation of completion and can classify long credits as watched. These are proposed product defaults, not values already accepted by the user.

## Stage 3: sorting/filtering and live Info

For API movie/series grids, offer **Provider order** (default), **Name A–Z**, **Rating highest first**, and **Recently added** for movies / **Recently updated** for series. Use the actual added/modified field with the correct label, not release date. Missing/invalid/zero ratings sort as unknown after meaningful ratings, with stable ties. Store choice per account/content type. Apply an **Unwatched** toggle to movie and episode lists; do not pretend all episodes in an unopened series are known. Reconcile focus when sorting/filtering hides or moves an item.

No complete movie year/genre/country/language filter or whole-library detail harvest is included. Those need a further indexing/source review. The existing global name search and Live TV category ordering are preserved.

Add a remote-focusable **Info** action to the lower live playback controller. Its small local popup shows the current channel/category, an explicit provider channel number when present, and the currently known video resolution/audio format. Use actual player format information; unknown fields stay unknown and a channel name containing 4K is not proof of 4K. Do not probe every stream or expose authenticated URLs, credentials, raw errors or internal decoder class names. Keep video visible outside the popup, playback intent and current position intact, and Back dismisses Info first. Retain native settings, Live, seek guards, timeout behavior and focus restoration. This does not add programme data or catch-up/archive playback.

## Data, memory and technology choice

Recommended approach: add an account-scoped **on-device SQLite index using Android's existing database API**, keeping the current M3U snapshot as the fallback. This adds no Room/database-library dependency or toolchain migration. SQLite allows querying small pages, filtering and sorting without retaining a second complete object graph. The cost is database/schema and identity-link maintenance alongside the existing snapshots.

A single JSON object graph would be simpler to deserialize but would retain more metadata in RAM. Replacing the full M3U with API-only startup could remove eager episode loading later, but would require a separately reviewed startup/account/favorites migration. MD-1 retains the full existing M3U, so it adds bounded metadata and does not claim an immediate reduction in baseline playlist memory or startup time.

- No provider API work is added to normal startup. Open Movies/Series to load that type's small index from disk first; only then fetch missing/stale data. Use the existing authenticated provider/account and request user-agent, with encoded query parameters and no protocol downgrade. Do not add external metadata services.
- Stream/validate responses off the main thread into a replacement transaction. Keep only the fields needed for listing/search in the index and query bounded pages for the visible grid. No list-wide detail fetches. Load one movie/series detail only when opened; series episodes are indexed from that selected series response rather than fetching every series.
- Listing age threshold is 24 hours; details may be reused for seven days. Cache refreshes are single-flight, cancellable and retain usable old data/age after failure. A library context action refreshes the opened type; the existing channel refresh can invalidate library freshness for the next opening, without starting additional requests at startup or changing the active player.
- Store metadata and viewing state in app-private no-backup storage. Cache/request ownership includes current credentials; favorite/progress account ownership follows the existing provider/user scope so a password rotation does not erase viewing history. Reject stale account completions and malformed/oversized responses without replacing a working cache. Keep sensitive URLs/values out of logs and tracked artifacts.
- Bound detail caching to a 20 MiB disk budget, with least-recently-used eviction of unopened details/episode metadata; progress/favorites are independent and never evicted by that policy. Retain the complete lightweight index for each successfully loaded type. Reuse Coil with visible-size artwork requests and bounded caches; no full-resolution backdrop/prefetch for every title and no image or icon dependency migration.

## Verification after approval

Use local synthetic API/playlist/media fixtures only. Add meaningful assertions for response streaming, account isolation/cancellation/atomic retention, typed IDs/duplicate aliases and legacy favorites/history survival; paging/cache age/eviction without progress loss; nested remote focus/Back/returns and current headings/adult-filter visibility; progress boundaries/persistence/save events/resume capability and no live tracking; deterministic sort/filter unknown fields; real native Info bounds/focus/format updates without URLs or full-picture shading. Test unavailable APIs and offline fallback, not only populated examples.

Run fresh smoke/lint and debug assembly with actual evidence separate from publication and physical acceptance. Keep normal smoke cases local and useful within the 90-second warm-run target; document any slower profiling/device exclusions honestly. A provider/device/deployment acceptance session remains separately approved. No Firestick installation, app start, live provider probe, release build or publication is included in this proposed local implementation approval.

## Decision requested

Approve MD-1 stages 1–3 (DATA-008, LIB-002, VOD-006, BROWSE-015, PLAY-014), including the explicit VOD-root/return/identity changes to BROWSE-013 and the watching defaults above, or identify the stages/defaults to revise. EPG, stronger adult-flag filtering, complete movie year/genre filters, trailers/external lookups, put.io, local/network libraries, touchscreen/mouse and faster-startup optimization remain outside this selected scope.

Proposal-stage maintenance: existing local smoke passed 153/153 in 33.9 seconds, with zero failures/skips/errors, after refreshing the catalog/mapping rows. All 115 requirement mappings are current. New/refined MD-1 coverage is explicitly manual/pending; baseline passes are not evidence of metadata implementation. No app source, implementation test, dependency/build behavior, provider/device state or deployment changed for this proposal.
