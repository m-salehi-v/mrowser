package net.mrowser.data

/** App-wide settings. Defaults are the shipped values. Immutable — update via copy(). */
data class Settings(
    val autoOpenPlayer: Boolean = true,
    val subtitleLanguage: SubtitleLanguagePref = SubtitleLanguagePref.ENGLISH,
    val cursorSpeed: CursorSpeed = CursorSpeed.NORMAL
)
