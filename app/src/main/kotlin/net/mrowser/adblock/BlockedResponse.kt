package net.mrowser.adblock

/** A 1×1 transparent GIF89a (42 bytes). Top-level so the enum below can reference it safely. */
private val GIF_1X1: ByteArray = byteArrayOf(
    0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00,
    0x00, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x21, 0xf9.toByte(), 0x04,
    0x01, 0x00, 0x00, 0x00, 0x00, 0x2c, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
    0x02, 0x01, 0x44, 0x00, 0x3b
)

/**
 * Pure: what to answer a blocked request with. An empty `text/plain` for an `<img>` fires the
 * page's `onerror` handlers and a bare empty body for an iframe breaks its layout, so the
 * stand-in is typed from the `Accept` header first, then the URL's extension.
 */
object BlockedResponse {

    enum class Kind(val mimeType: String, val body: ByteArray) {
        IMAGE("image/gif", GIF_1X1),
        HTML("text/html", ByteArray(0)),
        SCRIPT("application/javascript", ByteArray(0)),
        EMPTY("text/plain", ByteArray(0)),
    }

    private val IMAGE_EXTENSIONS = listOf(".gif", ".png", ".jpg", ".jpeg", ".webp", ".svg", ".avif", ".ico")

    fun kindFor(url: String, accept: String?): Kind {
        val a = accept?.lowercase().orEmpty()
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            a.startsWith("image/") || IMAGE_EXTENSIONS.any { path.endsWith(it) } -> Kind.IMAGE
            a.contains("text/html") -> Kind.HTML
            path.endsWith(".js") || path.endsWith(".mjs") -> Kind.SCRIPT
            else -> Kind.EMPTY
        }
    }
}
