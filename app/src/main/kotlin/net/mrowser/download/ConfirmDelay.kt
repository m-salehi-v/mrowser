package net.mrowser.download

/**
 * Pure: a confirm button ignores presses for [MS] after its dialog's window gains focus (the
 * moment it is really in front of the user, and again each time it comes back). A page can fire
 * a download from the user's own OK press, and the dialog then opens with Download focused; a
 * second press already on its way must not land on it.
 */
object ConfirmDelay {
    const val MS = 500L

    /** [armedAtMs] is when the window last gained focus; null while it has none. */
    fun accepts(armedAtMs: Long?, nowMs: Long): Boolean = armedAtMs != null && nowMs - armedAtMs >= MS
}
