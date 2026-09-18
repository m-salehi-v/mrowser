package net.mrowser.adblock

/**
 * Pure: a set of blocked domains, each meaning "this domain and every subdomain".
 *
 * Built from `res/raw/blocklist.txt` (one domain per line, `#` lines are metadata). Domains are
 * stored as sorted 64-bit FNV-1a hashes — about 0.8 MB for ~93k entries, versus several MB as
 * strings — and [contains] walks the host's suffixes (`a.b.c.d`, `b.c.d`, `c.d`) with a binary
 * search per step, stopping before the bare TLD. A collision at this size is a ~1e-9 event and
 * would only over-block one host. This is the scheme personalDNSfilter uses.
 */
class BlockList private constructor(
    private val hashes: LongArray,
    val info: Info
) {

    data class Source(val name: String, val url: String, val licence: String, val entries: Int?)

    data class Info(val generated: String?, val sources: List<Source>)

    val size: Int get() = hashes.size

    /** True when [host] or any parent domain of it (down to, but excluding, the TLD) is listed. */
    fun contains(host: String): Boolean {
        if (hashes.isEmpty()) return false
        var h = host.lowercase().trimEnd('.')
        while (true) {
            val dot = h.indexOf('.')
            if (dot < 0) return false
            if (java.util.Arrays.binarySearch(hashes, fnv1a64(h)) >= 0) return true
            h = h.substring(dot + 1)
        }
    }

    companion object {
        val EMPTY = BlockList(LongArray(0), Info(null, emptyList()))

        private val FNV_OFFSET: Long = 0xcbf29ce484222325uL.toLong()
        private const val FNV_PRIME: Long = 0x100000001b3L

        internal fun fnv1a64(s: String): Long {
            var h = FNV_OFFSET
            for (ch in s) {
                h = h xor ch.code.toLong()
                h *= FNV_PRIME
            }
            return h
        }

        /** Never throws: malformed lines are skipped, comments feed [Info]. */
        fun fromLines(lines: Sequence<String>): BlockList {
            var buf = LongArray(1024)
            var n = 0
            var generated: String? = null
            val sources = ArrayList<Source>()

            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    parseHeader(line)?.let { (g, s) ->
                        if (g != null) generated = g
                        if (s != null) sources.add(s)
                    }
                    continue
                }
                val domain = normalise(line) ?: continue
                if (n == buf.size) buf = buf.copyOf(buf.size * 2)
                buf[n++] = fnv1a64(domain)
            }

            val sorted = buf.copyOf(n)
            sorted.sort()
            var w = 0
            for (i in sorted.indices) {
                if (i == 0 || sorted[i] != sorted[i - 1]) sorted[w++] = sorted[i]
            }
            return BlockList(sorted.copyOf(w), Info(generated, sources))
        }

        /** Lowercase domain with any `*.`/leading/trailing dots removed, or null if not a domain. */
        private fun normalise(line: String): String? {
            val d = line.lowercase().removePrefix("*.").trim('.')
            if (d.isEmpty()) return null
            if (d.any { it.isWhitespace() || it == '/' || it == ':' }) return null
            if (!d.contains('.')) return null
            return d
        }

        /** Returns (generated, source) for a recognised header line, else null. */
        private fun parseHeader(line: String): Pair<String?, Source?>? {
            val body = line.removePrefix("#").trim()
            return when {
                body.startsWith("generated:") ->
                    body.removePrefix("generated:").trim().ifEmpty { null } to null
                body.startsWith("source:") -> {
                    val parts = body.removePrefix("source:").split('|').map { it.trim() }
                    val entries = parts.getOrNull(3)
                        ?.takeWhile { it.isDigit() }
                        ?.toIntOrNull()
                    null to Source(
                        name = parts.getOrElse(0) { "" },
                        url = parts.getOrElse(1) { "" },
                        licence = parts.getOrElse(2) { "" },
                        entries = entries
                    )
                }
                else -> null
            }
        }
    }
}
