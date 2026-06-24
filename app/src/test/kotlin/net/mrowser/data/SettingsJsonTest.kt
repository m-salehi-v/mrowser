package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsJsonTest {

    @Test fun `round trips all fields`() {
        val s = Settings(
            autoOpenPlayer = false,
            subtitleLanguage = SubtitleLanguagePref.PERSIAN,
            cursorSpeed = CursorSpeed.FAST
        )
        assertEquals(s, SettingsJson.fromJson(SettingsJson.toJson(s)))
    }

    @Test fun `missing fields fall back to defaults`() {
        assertEquals(Settings(), SettingsJson.fromJson("{}"))
    }

    @Test fun `blank input is all defaults`() {
        assertEquals(Settings(), SettingsJson.fromJson(""))
    }

    @Test fun `corrupt input is all defaults`() {
        assertEquals(Settings(), SettingsJson.fromJson("not json"))
    }

    @Test fun `unknown enum name falls back to default`() {
        val json = """{"autoOpenPlayer":true,"subtitleLanguage":"KLINGON","cursorSpeed":"WARP"}"""
        val s = SettingsJson.fromJson(json)
        assertEquals(SubtitleLanguagePref.ENGLISH, s.subtitleLanguage)
        assertEquals(CursorSpeed.NORMAL, s.cursorSpeed)
    }
}
