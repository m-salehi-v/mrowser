package net.mrowser.data

interface SettingsRepository {
    fun get(): Settings
    fun update(settings: Settings)
}
