package net.mrowser.stream

import net.mrowser.stream.MediaUrlClassifier.MediaKind

/**
 * Pure mapping of a sniffed URL to the MIME type the player should declare, so it builds an
 * HLS or DASH source rather than guessing from an extension a query string may hide. Null for
 * anything else: a progressive file is left to the media source factory, which reads the
 * container itself.
 */
object MediaMimeType {

    private const val HLS = "application/x-mpegURL"
    private const val DASH = "application/dash+xml"

    fun of(url: String): String? = when (MediaUrlClassifier.classify(url)) {
        MediaKind.MANIFEST_HLS -> HLS
        MediaKind.MANIFEST_DASH -> DASH
        else -> null
    }
}
