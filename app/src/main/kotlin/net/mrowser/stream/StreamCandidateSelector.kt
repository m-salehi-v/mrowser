package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind

/** Pure selection over collected stream candidates. */
object StreamCandidateSelector {

    /**
     * Prefer the master playlist (it declares the audio + subtitle renditions a variant lacks):
     * the NEWEST manifest whose URL contains "master" — newest so re-entry uses a freshly
     * re-issued master rather than a stale one — otherwise the earliest manifest seen.
     */
    fun selectBest(candidates: List<StreamCandidate>): StreamCandidate? {
        val manifests = candidates.filter {
            it.kind == MediaKind.MANIFEST_HLS && !MediaUrlClassifier.isAdHost(it.url)
        }
        val masters = manifests.filter { it.url.substringBefore('?').lowercase().contains("master") }
        return masters.maxByOrNull { it.seq } ?: manifests.minByOrNull { it.seq }
    }

    fun selectSubtitles(candidates: List<StreamCandidate>): List<StreamCandidate> =
        candidates
            .filter { it.kind == MediaKind.SUBTITLE }
            .sortedBy { it.seq }
            .distinctBy { it.url }
}
