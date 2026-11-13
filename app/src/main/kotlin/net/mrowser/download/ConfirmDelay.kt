package net.mrowser.download

/**
 * Pure: a confirm button ignores presses for [MS] after its dialog appears. A page can fire a
 * download from the user's own OK press, and the dialog then opens with Download focused; a
 * second press already on its way must not land on it.
 */
object ConfirmDelay {
    const val MS = 500L

    /** [shownAtMs] is null until the dialog is on screen. */
    fun accepts(shownAtMs: Long?, nowMs: Long): Boolean = shownAtMs != null && nowMs - shownAtMs >= MS
}
