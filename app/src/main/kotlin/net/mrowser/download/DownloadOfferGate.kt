package net.mrowser.download

/**
 * Pure: whether a page's download offer may open the confirm. One confirm at a time stops a page
 * stacking prompts but not repeating them — `setInterval(() => location.href = 'x.apk', 300)`
 * brings a new dialog back moments after every Cancel, so the user never reaches the bar or Home,
 * and a press of OK eventually lands after the [ConfirmDelay].
 *
 * So once the user declines an offer, every further offer on that same page is ignored until the
 * next page start ([reset] from `onPageStarted`, which a download never reaches) or the user's own
 * cursor click on the page ([reset] again — a click is the user asking for something new). A
 * decline for an offer made before the last reset is stale and changes nothing.
 */
class DownloadOfferGate {

    sealed interface Verdict {
        /** Show the confirm; pass [ticket] to [declined] if the user turns it down. */
        data class Offer(val ticket: Long) : Verdict

        /** Ignore the offer; [notify] is true for the first one ignored since the decline. */
        data class Blocked(val notify: Boolean) : Verdict
    }

    private var epoch = 0L
    private var declinedEpoch: Long? = null
    private var notified = false

    fun offer(): Verdict {
        if (declinedEpoch != epoch) return Verdict.Offer(epoch)
        val first = !notified
        notified = true
        return Verdict.Blocked(notify = first)
    }

    /** The user cancelled the confirm shown for [ticket] (Cancel, BACK, outside). */
    fun declined(ticket: Long) {
        if (ticket == epoch) declinedEpoch = epoch
    }

    /** A new page started, or the user clicked on the page: offers are welcome again. */
    fun reset() {
        epoch++
        declinedEpoch = null
        notified = false
    }
}
