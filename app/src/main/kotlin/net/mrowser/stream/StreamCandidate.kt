package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind

/** A media URL seen on the current page. `seq` orders by sighting. */
data class StreamCandidate(val url: String, val kind: MediaKind, val seq: Int)
