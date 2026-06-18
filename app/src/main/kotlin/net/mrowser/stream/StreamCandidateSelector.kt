package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind

/** Pure selection over collected stream candidates. */
object StreamCandidateSelector {

    /**
     * Prefer the master playlist: a URL containing "master" if present, otherwise the
     * earliest manifest seen (players fetch the master before its variants). Picking a
     * variant would lose the separate audio/subtitle renditions the master declares.
     */
    fun selectBest(candidates: List<StreamCandidate>): StreamCandidate? {
        val manifests = candidates.filter {
            it.kind == MediaKind.MANIFEST_HLS && !MediaUrlClassifier.isAdHost(it.url)
        }
        return manifests.firstOrNull { it.url.substringBefore('?').lowercase().contains("master") }
            ?: manifests.minByOrNull { it.seq }
    }

    fun selectSubtitles(candidates: List<StreamCandidate>): List<StreamCandidate> =
        candidates
            .filter { it.kind == MediaKind.SUBTITLE }
            .sortedBy { it.seq }
            .distinctBy { it.url }
}
