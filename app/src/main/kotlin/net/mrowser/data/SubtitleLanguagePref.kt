package net.mrowser.data

/**
 * Default-subtitle preference. [code] is the BCP-47 language tag (null = no preference);
 * [trackLabel] is the player-facing label baked onto the first side-loaded subtitle.
 */
enum class SubtitleLanguagePref(val code: String?, val trackLabel: String?) {
    PERSIAN("fa", "Persian"),
    ENGLISH("en", "English"),
    OFF(null, null)
}
