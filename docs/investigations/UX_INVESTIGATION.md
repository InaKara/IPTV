# Launcher, channel logos and connection timing

Initial read-only investigation on 22 October 2026 against `0bf7d1b`, followed by explicitly approved UI-004, DATA-003 and LOGIN-007 implementation. The first sections record baseline findings; the implementation/device results are below. Logo checks use local fixtures only, as requested. This report contains no provider/account addresses or credentials. Raw APK, screenshot, playlist and temporary benchmark sources remain under ignored `app/build/reports/investigation/`.

## Blue rectangle

The rectangle is the obsolete `res/drawable/ic_launcher.xml`: a blue square containing a solid white rectangle. Its two intended TV-leg paths have no stroke or filled area, so they do not draw; the white body sits above the center. It is an unfinished TV glyph, not a channel logo or an Android loading indicator.

Baseline `ChannelCard` in [HomeScreen.kt](../../app/src/main/java/com/example/iptv/ui/home/HomeScreen.kt) explicitly passed that drawable as Coil's error painter after an image request or decoding failure. Missing/empty logo URLs took a different path, displaying a blue PlayArrow. Coil's [2.5.0 AsyncImage source](https://github.com/coil-kt/coil/blob/2.5.0/coil-compose-base/src/main/java/coil/compose/AsyncImage.kt) defines this error-painter behavior.

The baseline loader included only `coil-compose:2.5.0`, with no SVG extension. A downloaded playlist sample contained:

| Metadata observation | Count |
| --- | ---: |
| Playlist entries | 100,915 |
| Entries without a nonblank logo URL | 14,709 |
| Logo URL paths ending in .svg | 118 |
| Logo URL paths ending in .avif | 4 |

URL extensions are clues, not decoded content-type proof. SVG needs Coil's optional decoder; see [Coil SVG support](https://coil-kt.github.io/coil/svgs/). This is a confirmed capability gap, but it does not establish the cause for each reported failing channel. Network failures, dead URLs or other unsupported formats can produce the same fallback. No claim is made that adding SVG makes all remote logos available. AVIF/other unsupported formats remain covered by the generic fallback rather than a promise of a new decoder on Android 9.

Automatic approval review rejected an attempted live check of one playlist-derived logo because its external destination/query had not been proven free of private data. A subsequent local search found no SVG sample on the strictly allowed public image hosts with all private-data checks satisfied; no third-party logo request was made. Provider-logo reachability remains unverified. A local synthetic SVG is sufficient for the proposed decoder regression test.

## Firestick app-selection tile

ADB metadata and a read-only copy of the installed package were inspected. The installed package's SHA-256 exactly matched the current local debug APK, ruling out an older APK in this comparison. Its compiled manifest/badging declared:

- Application icon: the classic `drawable-nodpi/app_logo.jpg` at every reported density.
- Application banner: `drawable/tv_banner.xml`, composing the classic image and app name.
- Launcher activity: `SplashActivity`, inheriting the application resources.

The obsolete drawable remains packaged because channel error rendering uses it. Its presence in the APK does not mean the current manifest chooses it as the launcher icon. Android TV exposes separate [icon and banner resources](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines), so resource-reference checks alone cannot establish the actual Fire TV tile appearance.

A stale launcher thumbnail is the leading hypothesis. It has **not** been proven by inspecting launcher-private cache data or reproducing the tile. The current screenshot showed the Firestick screensaver, not the app-selection screen; no remote keys were sent. Next steps after review are to inspect the actual tile, verify rendering of the supplied icon/banner, correct any resource compatibility issue, and use a non-destructive launcher refresh if needed. Do not clear launcher/app data or uninstall the app to force an icon change.

## Connection stages and measurements

Both the historical source at `7fbc654` and current repository download and parse the entire `get.php` playlist before Login succeeds. There is no separate account-authentication request. The word Connecting therefore includes downloading and parsing, not just establishing a socket or checking credentials. The later Loading channels screen groups/sorts already downloaded channels.

Current code also awaits `RecentlyWatchedManager.activate` before Login success. It reads/validates history when first needed, creates an index of the active playlist by stream URL, resolves account-specific history and can migrate legacy data. Historically recent-history deserialization happened in Home. This changes the location and nature of some preparation work; it does **not** move playlist downloading from Home to Login.

Home previously invoked both `loadChannels` and `refreshContent` in separate effects, potentially grouping twice. Current Home has one load effect, caches regular groups for the same playlist object, and receives reactive recent-list updates. This explains why the later phase has less application work. It does not by itself establish an end-to-end speedup.

One provider request was measured from the development PC using the private local configuration, the same endpoint/query shape and OkHttp user-agent, automatic gzip/deflate decompression and no parser:

| Measurement | Result |
| --- | ---: |
| Time to response headers | 3.32 s |
| Time to first body byte | 3.35 s |
| Complete response transfer, including wait | 4.67 s |
| Decoded playlist size | 27.37 MiB |

This is one PC sample, not a Firestick benchmark or an old-versus-new network comparison. Provider wait, routes, compression and network conditions can vary. No device-side phase timings were available in the current app.

For an offline comparison, the actual historical parser was compiled separately under the ignored investigation folder, and both parsers read the same downloaded bytes using JDK 17. Production sources/build configuration were unchanged. After warm-up, three alternating runs gave:

| Offline desktop operation | Result |
| --- | --- |
| Historical parser | 0.605 / 0.597 / 0.602 s |
| Current parser | 0.345 / 0.377 / 0.369 s |
| Parsed entry count, both | 100,915 |
| Java equivalent of the new stream-URL index | 0.020 s |
| Java equivalent of category grouping | 0.016 s; 179 groups |

The index/grouping probes approximate the corresponding collection operations; they do not run the Android history manager, disk preferences or UI. Cold runs were order/JIT dependent and are not used to claim a comparative improvement. These measurements provide no evidence of a parser regression on the PC, but Android CPU, memory pressure and collection costs differ. They cannot quantify the Firestick's added history cost.

## Proposed correction and verification

1. **UI-004:** keep the chosen classic branding; verify/correct the actual Fire TV tile and any launcher refresh needed while preserving app data. Strengthen resource/rendering checks beyond simply checking resource IDs, then obtain device visual evidence.
2. **DATA-003:** use one centered play-symbol fallback for missing/failed/unsupported images, remove the old blue/white drawable from the channel path, and add the matching Coil 2.5.0 SVG extension. Verify raster/SVG success, fallback and caching using local fixtures before checking the Firestick.
3. **LOGIN-007:** add debug-only phase durations/counts without URLs, credentials, channel names or raw exception messages. Separate provider response wait from the overlapping streaming download/parser span, then history preparation, Home grouping and total Connect-to-usable-list time. Use device measurements to choose any further optimization; changing labels alone is not a performance improvement.

The initial read-only investigation performed no application changes or deployment; its only completed live content request was the playlist request. The user subsequently approved the implementation below. No persistent playlist cache, authentication change, automatic login or artwork redesign was introduced. A later, separately approved uninstall/reinstall restored the verified original preferences. The Android toolchain and Coil version remain unchanged; the test-only Robolectric dependency moved to 4.12.2 because 4.11.1 does not supply native Windows graphics support.

## Approved implementation and device results

- **Channel images:** matching Coil 2.5.0 SVG extension; one centered gray play-symbol painter for loading, blank/missing data, failed requests and unsupported formats. PNG/SVG decoding, ratio/pixels, memory/disk cache reuse and equal fallback pixels have local regression checks. The Firestick five-channel fixture visibly showed both green raster/SVG rectangles and matching arrows for missing/404/unsupported logos. No external channel-logo probes were performed.
- **Launcher resources:** unchanged classic artwork packaged as Android TV PNG sizes 80/120/160/240/320 under mipmap densities, explicit icon/banner on SplashActivity, and removal of the obsolete blue/white drawable. Native rendering checks compare the actual application/activity icon against the classic asset and render the banner. These changes were first installed as 1.1.1 (3); the final migrated version is recorded below.
- **Original-ID launcher issue:** the actual Home and app-list thumbnail showed the old icon after replacement updates and a reboot. Fire OS refused a launcher force-stop. The user then explicitly approved uninstall/reinstall with backup restoration. A fresh backup matched the original, uninstall/install succeeded, original preferences were restored exactly and the app launched. The app-list tile still showed the rectangle, including after a further restart following the user's VPN disconnection. Selecting that tile was confirmed to open com.example.iptv/.MainActivity. This failed the original-ID visual acceptance; the subsequently approved migration below resolved it. No launcher-data reset or third-party launcher was attempted.
- **Identity isolation:** an ignored Gradle initialization script built a temporary com.example.iptv.logoprobe APK with unchanged artwork/resources and empty account defaults. Fire TV displayed its correct classic icon directly beside the original-ID blue/white tile. This establishes that the artwork can render on this launcher and isolates the difference to the installation identity; the launcher's underlying cached/catalog metadata source was not inspected. The temporary copy was removed, normal build outputs restored, and original preferences again verified byte-for-byte. This led to the reviewed OPS-006 migration below.
- **Diagnostics:** StartupTimings records fixed phase/outcome names and numbers only; attempt identity prevents stale retries from finishing a new trace, and successful first-list layout ends the total. Cancellation/failure closes the attempt. Fire OS has `persist.log.tag=I`, so debug builds emit these records at INFO under AypiStartup; release builds disable the trace. No global device logging setting was changed.
- **Checks:** the logo/timing work passed 88/88 automatic checks with debug lint and release build/lint. OPS-006 adds three deployment checks and strengthens the compiled identity/launcher-intent case, making 91 automatic checks: 51 app, eight per environment reader, 18 runner and six project checks. Deployment doubles verify install -r, the new-ID/unchanged-class target, failure ordering and rejection of a launch error despite zero exit status. Native image tests use a Canvas capture compatible with Compose 1.6; test fixtures run under Windows PowerShell 5.1 as well as the current shell. The generated requirement rows retain actual latest host outcomes.

Three successful fresh-process Firestick runs used the same **synthetic 100,915-entry / 24.21 MiB playlist**, served locally through an ADB loopback bridge. The fixture deliberately waited 350 ms before sending headers. Times are milliseconds:

| Phase | Run 1 | Run 2 | Run 3 |
| --- | ---: | ---: | ---: |
| Provider wait, including connection/queue | 461 | 390 | 413 |
| Streaming download + parsing | 15,726 | 15,073 | 17,585 |
| Account/history preparation | 355 | 414 | 353 |
| Category grouping | 166 | 268 | 172 |
| Total to first browsing-list layout | 18,201 | 17,651 | 19,821 |

The total also includes scheduling, credential save, route transition and first layout; it is measured independently, not obtained by adding spans. Download and parsing overlap, so this table cannot attribute their combined span to CPU versus transport. A local ADB bridge serving an uncompressed synthetic list is not equivalent to the real provider path. No historical Firestick benchmark exists, so these results do not prove a real-provider regression or a speedup. They do show that added history preparation is small compared with the bulk playlist span, and that later category grouping is brief. The earlier desktop current-versus-historical parser comparison remains separate evidence.

All temporary device fixture settings were removed afterward. The original shared-preference file, containing saved credentials/history, was restored and verified byte-for-byte, including after the approved fresh installation and restart. The local bridge was removed and the updated app was left on its normal login screen. Screenshots and raw data remain ignored/private; generated Manual rows refer to checks not executed by the host suite, while this section records separate device observations.

## Approved permanent identity migration

The user approved OPS-006 with “Approved, implement.” The real application ID is now **com.inakara.aypitivi**, while Kotlin namespace **com.example.iptv** and the signing key are unchanged. The exported component is `com.inakara.aypitivi/com.example.iptv.SplashActivity`; build, deployment, compiled manifest/intent tests and documentation agree on that pair.

The original app was stopped and a fresh private preference backup taken. Original/new APK signing certificate digests matched. The new identity was installed without removing the original, its process stopped, and the original preference file restored without text re-encoding. Read-back SHA-256 matched exactly. Its classic tile was visually verified and selecting it opened the migrated MainActivity.

The ordinary deployment script then built, installed and launched a real update from **1.1.1 (3) to 1.1.2 (4)**. Installed version and saved-preference hash were verified; the app list retained the correct classic icon. Only afterward was the obsolete com.example.iptv installation removed. The final app list showed the remaining classic tile without the blue/white entry; its launch reached the new package's MainActivity, and preferences still matched the original byte-for-byte. UI-004 and OPS-006 are Implemented with this separate device evidence. No real-provider playback or remote-logo availability is inferred.

Final host verification passed **91/91** fresh checks in 27.2 seconds plus debug lint. The final 1.1.2 release build/lint passed and generated release account defaults were verified empty. The installed Firestick artifact is debug; optimized release runtime remains unverified.

The user's future **LOGIN-008** direction is startup straight into channel-list loading, with download/cache/refresh behavior still undecided. It remains Open for later review. No optimization specific to the current Connect-button workflow was added; repository/history/grouping/layout timings can be reused by that future entry path.
