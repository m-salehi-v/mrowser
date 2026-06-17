package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind

/** Pure selection over collected stream candidates. */
object StreamCandidateSelector {

    fun selectBest(candidates: List<StreamCandidate>): StreamCandidate? =
        candidates
            .filter { it.kind == MediaKind.MANIFEST_HLS && !MediaUrlClassifier.isAdHost(it.url) }
            .maxByOrNull { it.seq }

    fun selectSubtitles(candidates: List<StreamCandidate>): List<StreamCandidate> =
        candidates
            .filter { it.kind == MediaKind.SUBTITLE }
            .sortedByDescending { it.seq }
            .distinctBy { it.url }
}
