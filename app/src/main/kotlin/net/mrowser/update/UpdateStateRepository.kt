package net.mrowser.update

/** Storage for the update channel's cache. */
interface UpdateStateRepository {
    /** An immutable snapshot; safe to read from any thread. */
    fun get(): UpdateState

    fun update(state: UpdateState)
}
