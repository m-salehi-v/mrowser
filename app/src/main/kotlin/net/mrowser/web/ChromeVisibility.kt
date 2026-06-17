package net.mrowser.web

/** Pure visibility reducer for the address-bar overlay. */
object ChromeVisibility {

    enum class State { HIDDEN, VISIBLE }

    sealed interface Event {
        data class RevealRequested(val atTop: Boolean) : Event
        data object Interacted : Event
        data object PageInteracted : Event
        data object IdleElapsed : Event
    }

    fun reduce(state: State, event: Event): State = when (state) {
        State.HIDDEN -> when (event) {
            is Event.RevealRequested -> if (event.atTop) State.VISIBLE else State.HIDDEN
            else -> State.HIDDEN
        }
        State.VISIBLE -> when (event) {
            is Event.PageInteracted, is Event.IdleElapsed -> State.HIDDEN
            is Event.Interacted -> State.VISIBLE
            is Event.RevealRequested -> State.VISIBLE
        }
    }
}
