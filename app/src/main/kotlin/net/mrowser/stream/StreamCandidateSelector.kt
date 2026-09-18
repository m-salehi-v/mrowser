package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind

/** Pure selection over collected stream candidates. */
object StreamCandidateSelector {

    /**
     * Tiers, best first: HLS, DASH, then a progressive file (an .mp4 is only ever the fallback
     * for a page that serves no manifest at all). Within HLS, prefer the master playlist (it
     * declares the audio + subtitle renditions a variant lacks): the NEWEST manifest whose URL
     * contains "master" — newest so re-entry uses a freshly re-issued master rather than a stale
     * one — otherwise the earliest manifest seen. The other tiers take the earliest sighting.
     */
    fun selectBest(candidates: List<StreamCandidate>): StreamCandidate? {
        val playable = candidates.filter { !MediaUrlClassifier.isAdHost(it.url) }
        val manifests = playable.filter { it.kind == MediaKind.MANIFEST_HLS }
        val masters = manifests.filter { it.url.substringBefore('?').lowercase().contains("master") }
        return masters.maxByOrNull { it.seq }
            ?: manifests.minByOrNull { it.seq }
            ?: playable.filter { it.kind == MediaKind.MANIFEST_DASH }.minByOrNull { it.seq }
            ?: playable.filter { it.kind == MediaKind.PROGRESSIVE }.minByOrNull { it.seq }
    }

    fun selectSubtitles(candidates: List<StreamCandidate>): List<StreamCandidate> =
        candidates
            .filter { it.kind == MediaKind.SUBTITLE }
            .sortedBy { it.seq }
            .distinctBy { it.url }
}
