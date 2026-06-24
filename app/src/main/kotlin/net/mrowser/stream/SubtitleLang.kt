package net.mrowser.stream

/**
 * Pure: guesses a subtitle's language from its URL (path/query/filename — never the host),
 * so the player can show a real name ("English", "Persian") instead of a generic label.
 * Side-loaded subtitles carry no metadata, so this is a best-effort heuristic; callers fall
 * back to a generic "Subtitle N" when [detect] returns null.
 */
object SubtitleLang {

    /** A detected language: BCP-47 [code] for the track tag, human [label] for display. */
    data class Detected(val code: String, val label: String)

    private data class Lang(val code: String, val label: String, val tokens: List<String>)

    // Tokens are matched whole (between non-alphanumerics), lowercased — so "es" matches a
    // bare "es" segment but never the "es" inside "files".
    private val LANGS = listOf(
        Lang("en", "English", listOf("en", "eng", "english")),
        Lang("fa", "Persian", listOf("fa", "far", "fas", "persian", "farsi", "pes")),
        Lang("ar", "Arabic", listOf("ar", "ara", "arabic")),
        Lang("fr", "French", listOf("fr", "fra", "fre", "french")),
        Lang("es", "Spanish", listOf("es", "spa", "spanish", "espanol")),
        Lang("de", "German", listOf("de", "deu", "ger", "german")),
        Lang("it", "Italian", listOf("it", "ita", "italian")),
        Lang("tr", "Turkish", listOf("tr", "tur", "turkish")),
        Lang("ru", "Russian", listOf("ru", "rus", "russian")),
        Lang("pt", "Portuguese", listOf("pt", "por", "portuguese")),
        Lang("hi", "Hindi", listOf("hi", "hin", "hindi")),
        Lang("zh", "Chinese", listOf("zh", "zho", "chinese")),
        Lang("ja", "Japanese", listOf("ja", "jpn", "japanese")),
        Lang("ko", "Korean", listOf("ko", "kor", "korean"))
    )

    fun detect(url: String): Detected? {
        // Path + query only — drop the scheme://host so a host like "en.cdn.com" isn't misread.
        val tail = url.substringAfter("://", "").substringAfter('/', "").ifEmpty { url }
        val tokens = tail.lowercase().split(Regex("[^a-z0-9]+")).filterTo(HashSet()) { it.isNotEmpty() }
        return LANGS.firstOrNull { lang -> lang.tokens.any { it in tokens } }
            ?.let { Detected(it.code, it.label) }
    }
}
