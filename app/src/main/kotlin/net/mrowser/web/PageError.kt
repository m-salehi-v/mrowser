package net.mrowser.web

/** What went wrong with a main-frame load, in the terms the error screen speaks. */
enum class PageErrorKind { NO_INTERNET, NOT_FOUND, TIMED_OUT, CONNECTION, NOT_SECURE, SERVER, CRASHED, OTHER }

/**
 * Turns a `WebViewClient.onReceivedError` report into a [PageErrorKind], or null when the
 * navigation was cancelled rather than failed (so the error screen must stay away).
 *
 * The Chromium description (`net::ERR_...`) is the precise signal; the `WebViewClient.ERROR_*`
 * code is coarse and several net errors share one (`ERROR_UNKNOWN`), so the description wins and
 * the code only decides when the description says nothing we know.
 */
object PageError {
    // WebViewClient.ERROR_* values, as literals to keep this module free of Android imports.
    private const val UNSUPPORTED_SCHEME = -10
    private val CODE_KINDS = mapOf(
        -2 to PageErrorKind.NOT_FOUND, // HOST_LOOKUP
        -6 to PageErrorKind.CONNECTION, // CONNECT
        -8 to PageErrorKind.TIMED_OUT, // TIMEOUT
        -11 to PageErrorKind.NOT_SECURE, // FAILED_SSL_HANDSHAKE
    )

    /** Names for a navigation that was cancelled, not failed: our own ad cancel, a download, a scheme. */
    private val CANCELLED = setOf("ERR_ABORTED", "ERR_UNKNOWN_URL_SCHEME", "ERR_BLOCKED_BY_CLIENT")

    private class Rule(val kind: PageErrorKind, val matches: (String) -> Boolean)

    /** First match wins, so `ERR_CONNECTION_TIMED_OUT` is a timeout before it is a connection error. */
    private val RULES = listOf(
        Rule(PageErrorKind.TIMED_OUT) { it.endsWith("TIMED_OUT") },
        Rule(PageErrorKind.NO_INTERNET) { it == "ERR_INTERNET_DISCONNECTED" || it == "ERR_NETWORK_CHANGED" },
        Rule(PageErrorKind.NOT_FOUND) { it == "ERR_NAME_NOT_RESOLVED" || it == "ERR_NAME_RESOLUTION_FAILED" },
        Rule(PageErrorKind.NOT_SECURE) { it.startsWith("ERR_CERT_") || it.startsWith("ERR_SSL_") },
        Rule(PageErrorKind.CONNECTION) { it.startsWith("ERR_CONNECTION_") || it == "ERR_ADDRESS_UNREACHABLE" },
    )

    /** Null means "not an error to show". */
    fun classify(code: Int, description: String?): PageErrorKind? {
        val name = codeLabel(description)
        if (name in CANCELLED || code == UNSUPPORTED_SCHEME) return null
        return RULES.firstOrNull { it.matches(name) }?.kind ?: CODE_KINDS[code] ?: PageErrorKind.OTHER
    }

    /** The description without its `net::` prefix, for showing as a code line. */
    fun codeLabel(description: String?): String = description.orEmpty().removePrefix("net::")

    /**
     * Evaluates to `true` when the page has nothing to see: no body, or no non-blank text and no
     * image, video, frame, canvas or svg. One expression that cannot throw.
     */
    const val EMPTY_PAGE_SCRIPT: String =
        "(function(){try{var b=document.body;if(!b)return true;" +
            "return !/\\S/.test(b.innerText||'')&&!b.querySelector('img,video,iframe,canvas,svg');" +
            "}catch(e){return false;}})()"

    /** `evaluateJavascript` hands the expression's value back as JSON, so `true` arrives as `"true"`. */
    fun isEmptyPage(scriptResult: String?): Boolean = scriptResult?.trim() == "true"
}
