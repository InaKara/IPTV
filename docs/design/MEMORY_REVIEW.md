# Memory reduction — MR-1

23 October 2026. The user asked for a plain-language breakdown and a recommended solution/next analysis. REL-004 was explicitly approved with “Mr-1 approved” on 23 October 2026. Local preservation checks and representative measurements are included; Android profiling, deployment, index redesign and provider/API partitioning remain separate. Existing parser/catalogue behavior and AU-1 decoder implementation are separate scopes.

## Where memory goes

- The complete channel list retains names, stream/logo addresses and other metadata for about 101,000 entries. In the desktop diagnostic that graph occupied about 52–55 MiB.
- Browser stable IDs, canonical entries, categories and lookup structures added about 21 MiB; the full recent-history stream lookup added about 6 MiB despite history retaining only 20 references. Extra home-list reference copies are much smaller, about 1 MiB of allocation.
- A refresh keeps working/playing data while the replacement is prepared. The desktop overlap model retained about 151–154 MiB. Some overlap protects working playback and failed-refresh retention; simply dropping the old list is not an acceptable fix.
- The UI, runtime, decoded logos and native audio/video buffers add memory outside that simple channel-data model. Native/graphics memory is not interchangeable with the Java heap budget. Snapshot/APK/image-cache files on disk use storage; loading/decoding them creates RAM use.

Desktop results are not Android memory measurements or a reproduction of the historical crash. The Firestick's normal app heap limit is 192 MiB.

## New sharing experiment

The current compiled parser read the existing private playlist through InputStream. A scratch-only graph replacement reused equal category/user-agent values and reused a displayed name when its TVG name had exactly the same value. App source, saved data and provider data were not changed.

| Field | Distinct string objects | Distinct values |
| --- | ---: | ---: |
| Category | 100,867 | 179 |
| User-agent | 100,961 | 1 |
| Logo address | 86,253 | 61,900 |
| TVG name | 100,961 | 99,403 |
| Display name | 100,961 | 99,423 |

100,862 entries had equal but separately retained display/TVG names. Sharing only category/user-agent and those equal-name pairs reduced the scratch graph from **52.38 to 33.65 MiB**, saving **18.73 MiB** after collection. This is a desktop candidate saving, not an Android guarantee. No URL, name, case, whitespace or metadata value was removed or normalized.

## Recommended first scope

Approved REL-004: perform that limited value sharing during both playlist parsing and snapshot loading, using small per-load category/user-agent pools and per-entry equal-name reuse. Avoid unbounded process-global interning or building a second complete replacement graph just to deduplicate it. Keep canonical IDs, duplicate occurrences, provider metadata, snapshot schema, account separation, cancellation and failure retention unchanged.

Validate exact metadata/IDs and parser/snapshot round trips, then compare the same representative initial/cache/refresh workload's allocation and retained memory. Obtain Android peaks before claiming a crash fix. Stage a separate review for redundant indexes and retained old collections if the first saving is insufficient; do not remove failed-refresh/playback continuity guarantees. Loading Live TV separately from the much larger VOD/episode collection could save more but is a broader provider/cache/library architecture decision, not MR-1.

## MP2 memory impact

The pinned official renderer has 16 input/output buffers. Its default input capacity totals about 90 KiB; configured output capacities total about 1 MiB for 16-bit PCM or 2 MiB for float PCM when fully allocated. Decoder context, resampling, native code and sink buffers add to that, and actual input requirements can differ. The decoder buffers are created when the software decoder is used; adding an extension does not create another full playlist. Exact runtime delta and packaged native code size are separate measurements to record during AU-1 validation. These source bounds do not substitute for Firestick memory profiling.

AU-1 local build now measures 344,068 bytes for the ARMv7 library and 416,328 for ARM64; the debug APK increased by 345,343 bytes (about 337 KiB) against the previous local build. Native source/ABI/PCM checks and 140/140 smoke/lint passed. These are code/storage measurements, not runtime RAM. No device memory delta or audible acceptance has been measured. See [FFMPEG_BUILD.md](../operations/FFMPEG_BUILD.md).

## MR-1 implementation and local comparison

REL-004 now shares exact category and user-agent values through pools capped at 512 and 32 distinct values respectively. Existing pooled values remain reusable after a cap is reached; other values remain intact. Each parser call or snapshot read owns fresh pools. Exactly equal display/TVG names share the display-name reference within their entry. URLs and other fields are untouched. No global interning, schema migration, channel deletion, index redesign or refresh/lifecycle change is involved.

Snapshot reading retains Gson's existing field/null/unknown-field semantics through a delegated adapter. It replaces at most one channel object at a time while streaming, rather than constructing a second full graph. The reader and its pools are discarded after that read. The tradeoff is some extra short-lived entry allocations during cached loading, measured below; the retained channel data is substantially smaller.

Before editing, a standalone diagnostic measured the compiled original parser and Gson snapshot reader. After compilation, the same diagnostic used the implemented parser and the actual shared snapshot-reader factory. Both used JDK 17, a 512 MiB desktop heap, the same offline 100,961-entry private playlist and snapshot schema, fresh initial/cached processes and one refresh with the original catalogue/history retained. No provider or device was contacted. These are single-sample retained HotSpot heap readings after forced collection, including harness/runtime objects, not Android peaks or timing targets.

| Retained heap after phase | Before | MR-1 | Reduction |
| --- | ---: | ---: | ---: |
| Initial playlist parse | 52.68 MiB | 34.26 MiB | 18.42 MiB |
| Cached snapshot read | 55.06 MiB | 36.76 MiB | 18.30 MiB |
| Initial scenario, old/new refresh catalogues retained | 151.15 MiB | 114.25 MiB | 36.90 MiB |
| Cached scenario, old/new refresh catalogues retained | 153.84 MiB | 116.86 MiB | 36.98 MiB |

Cumulative parse allocation stayed approximately unchanged (203.56 to 203.85 MiB on initial parse; 197.41 MiB on both warmed refresh parses). Cached-read cumulative allocation increased from 103.59 to 108.94 MiB, about 5.35 MiB, because the delegated streaming adapter briefly constructs replacement entries. This is allocation churn, not retained RAM. Catalogue hashing/index allocations remain outside MR-1; there is no loading-speed or crash-resolution claim. Summed per-pool peak counters are not simultaneous heap peaks and are excluded from these conclusions.

Full representative comparisons confirm every metadata value and canonical occurrence ID matches the pre-change snapshot for both parser and shared-reader outputs. Both retain 179 category string objects, one user-agent string object and 100,862 shared equal-name pairs. Snapshot output bytes are identical. Private diagnostic inputs, sources and results remain in ignored build reports.

TC-APP-107–110 cover exact synthetic parser/snapshot metadata, duplicate occurrence IDs, case/blank/null preservation, pool limits, interrupted parser input and independent loads/accounts. Existing cache corruption, atomic replacement, cancelled/superseded account edits, failed refresh and browser retention checks remain in the fresh suite. Local smoke/debug lint passed 150/150 with zero failures/skips/errors; lint has zero errors and 31 warnings, and debug assembly passed. Android initial/cache/refresh peaks, runtime decoder delta and physical playback continuity remain pending. No installation or app start occurred; historical OOM resolution remains unproven.
