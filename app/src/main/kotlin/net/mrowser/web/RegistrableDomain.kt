package net.mrowser.web

/**
 * Pure: the registrable domain (eTLD+1) of a host, by a small heuristic rather than the full
 * public suffix list.
 *
 * `www.example.com` → `example.com`; `a.example.co.uk` → `example.co.uk`. Used to decide
 * "first-party" for the ad blocker and as the key of the per-site allowlist. Two-label public
 * suffixes are the common country-code ones; a host under a suffix not listed here just
 * collapses to its last two labels, which only makes first-party detection slightly wider.
 */
object RegistrableDomain {

    private val TWO_LABEL_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "ltd.uk", "plc.uk",
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "com.br", "net.br", "org.br", "gov.br",
        "co.in", "net.in", "org.in", "gov.in",
        "co.za", "org.za", "gov.za",
        "com.mx", "com.tr", "co.kr", "or.kr", "com.ar", "com.cn", "net.cn", "org.cn",
        "com.tw", "com.hk", "co.nz", "org.nz", "com.sg", "com.my", "co.id", "com.ua",
        "com.pl", "com.ru", "com.eg", "com.sa", "com.pk", "com.ph", "com.vn", "com.ng",
        "co.il", "com.co", "com.pe", "com.ve", "com.hr", "co.th", "com.bd", "com.np"
    )

    fun of(host: String): String {
        val h = host.lowercase().trimEnd('.')
        if (h.isEmpty() || h.contains(':') || isIpv4(h)) return h
        val labels = h.split('.')
        if (labels.size <= 2) return h
        val lastTwo = labels[labels.size - 2] + "." + labels[labels.size - 1]
        val keep = if (lastTwo in TWO_LABEL_SUFFIXES) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    private fun isIpv4(h: String): Boolean =
        h.count { it == '.' } == 3 && h.all { it.isDigit() || it == '.' }
}
