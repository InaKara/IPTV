# UX-2 — bounded audio and subtitle inspection

26 October 2026. PLAY-016/017 were explicitly approved for bounded PC investigation after the UX-2 review. No Firestick connection, app start, playback/listening, installation, setting change, provider mutation or app progress change occurred. Private one-off probes and binary prefixes remain ignored under app/build; authenticated addresses, provider IDs, unrelated titles and raw responses are omitted here.

## Results

One provider variant matching The Pitt was selected from three matching series entries. Its season-two episode-one and episode-two routes were inspected; the exact variant selected on the user's installed app has not been verified. One non-adult movie was also sampled. It is not a user-confirmed normally sounding control.

| Sample | Audio tracks declared in complete Matroska TrackEntries | Subtitle tracks declared |
| --- | --- | --- |
| The Pitt S02E01, sampled variant | English and Turkish E-AC-3, each six channels at 48 kHz; no stereo alternative declared | One Turkish UTF-8/SubRip text track |
| The Pitt S02E02, same variant | English and Turkish E-AC-3, each six channels at 48 kHz; no stereo alternative declared | One Turkish UTF-8/SubRip text track |
| Sampled movie, unconfirmed control | Turkish AAC stereo at 48 kHz | No subtitle TrackEntry in the inspected complete Tracks element |

Track entries declare capabilities, not the quality of the source mix, selected Firestick track, supported output path or actual speaker channel mapping. Six channels alone do not establish the echo's cause. The user reports output through a soundbar and additional speakers, with no TV-speaker output, and suspects affected audio quality rather than setup. No setup change is proposed.

The episode and movie headers omit explicit Default/Forced flag elements. The [Matroska element specification](https://www.matroska.org/technical/elements.html) defines omitted FlagDefault as 1 and FlagForced as 0. Thus the tracks are eligible for default selection, and the Turkish subtitle is not marked forced; this does not prove which track Android selects or that subtitles are currently unused. Burned-in text was not checked because no video was played or decoded for visual inspection.

## Current app capability and limits

The pinned [Media3 1.2.1 Matroska extractor](https://github.com/androidx/media/blob/1.2.1/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java) recognizes E-AC-3 and UTF-8/SubRip, and passes subtitle selection flags to the player. This is container/parser support; actual device audio decoding/output and subtitle rendering still need runtime acceptance.

TvPlayerView retains the native subtitle surface and default track selection, but does not enable a visible subtitle chooser. The pinned [native controller](https://github.com/androidx/media/blob/1.2.1/libraries/ui/src/main/java/androidx/media3/ui/PlayerControlView.java) defaults that chooser to hidden. An available default subtitle may still be selected/rendered automatically; no installed-app selection was observed. Library metadata has no per-title subtitle inventory and cannot establish availability for every movie/series. No subtitle UI, language preference, external subtitle service or audio policy/decoder change was part of this approval.

## Bounds and method

The successful probes consumed **14,088,713 response bytes total (about 13.44 MiB)** across metadata discovery and three **512 KiB** stream-prefix samples. Per-title slots, including associated discovery, consumed 5,227,032; 524,288; and 8,337,393 bytes respectively. Successful probe execution time summed to about 5.32 seconds, excluding later offline parsing and implementation work. This remains below 16 MiB/60 seconds per title and 64 MiB/four minutes of probe execution overall. No complete movie/episode was downloaded.

The first sandbox request returned no data; the authorized network retry succeeded. Standard ffprobe could not finish the two episode prefixes because large attachment data follows their Tracks element. Offline direct EBML parsing of the already downloaded bytes recovered the complete track lists without further provider reads. The movie's bounded ffprobe result and independent local TrackEntries inspection agree on AAC stereo and no declared subtitle track. No full attachment section, cue/index or media payload was fetched to force a result.

## Conclusion and next evidence

Selectable Turkish subtitles exist in these two sampled episodes. The sampled movie has none declared; other films/series and other provider variants remain unknown. A visible CC/language selector can be scoped separately if requested; this investigation does not authorize implementing it.

PLAY-016 remains Partial: format/layout differences are established, but no user-confirmed normal title or audible comparison exists, and the echo has not been reproduced or fixed. A useful next separately approved session would verify the exact affected variant, current English/Turkish selection and native audio/output diagnostics, then compare a short affected segment with a known-normal source through the same soundbar/speakers. Changing decoder, passthrough, channel limits or TV settings before that evidence would be speculative. PC metadata inspection needs nobody to listen; accepting an eventual audible correction does require a short listening check.

PLAY-017 remains Partial for source/device selection/rendering acceptance and the uninspected catalog; the bounded three-title track inventory itself is complete. No general subtitle availability or echo-resolution claim is made.
