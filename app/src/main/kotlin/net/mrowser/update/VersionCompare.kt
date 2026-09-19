package net.mrowser.update

/**
 * Pure comparison of dotted numeric version strings ("1.4.0", "v1.10.0").
 *
 * Segments compare as numbers, so 1.10.0 beats 1.9.0 where string order would not, and a
 * missing segment counts as zero so "1.4" and "1.4.0" are the same release. Anything that is
 * not purely numeric comes back `null` — unknown, never a guess: a background check that
 * cannot read a version must say "no update" rather than nag about one that may not exist.
 */
object VersionCompare {

    /** -1 / 0 / 1 as [a] is older / same / newer than [b]; null when either is unreadable. */
    fun compare(a: String, b: String): Int? {
        val left = segments(a) ?: return null
        val right = segments(b) ?: return null
        for (i in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return if (l < r) -1 else 1
        }
        return 0
    }

    /** True only when [candidate] is strictly newer; an unreadable version is never newer. */
    fun isNewer(candidate: String, installed: String): Boolean =
        (compare(candidate, installed) ?: 0) > 0

    /** "v1.4.0" -> [1, 4, 0]; null when any segment is not a non-negative integer. */
    private fun segments(version: String): List<Int>? {
        val trimmed = version.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty()) return null
        return trimmed.split('.').map { part ->
            val n = part.toIntOrNull() ?: return null
            if (n < 0) return null
            n
        }
    }
}
