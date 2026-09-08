package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsJsonTest {

    @Test fun `round trips all fields`() {
        val s = Settings(autoOpenPlayer = false, cursorSpeed = CursorSpeed.FAST)
        assertEquals(s, SettingsJson.fromJson(SettingsJson.toJson(s)))
    }

    @Test fun `round trips the pop-up blocker`() {
        val s = Settings(blockPopups = false)
        assertEquals(s, SettingsJson.fromJson(SettingsJson.toJson(s)))
    }

    @Test fun `the pop-up blocker is on when the field is absent`() {
        assertTrue(SettingsJson.fromJson("{\"autoOpenPlayer\":true}").blockPopups)
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
        val s = SettingsJson.fromJson("""{"autoOpenPlayer":true,"cursorSpeed":"WARP"}""")
        assertEquals(CursorSpeed.NORMAL, s.cursorSpeed)
    }

    @Test fun `round trips the internal flags`() {
        val s = Settings(seeded = true, navHintShown = true)
        assertEquals(s, SettingsJson.fromJson(SettingsJson.toJson(s)))
    }

    @Test fun `internal flags default to false for a pre-upgrade file`() {
        val s = SettingsJson.fromJson("""{"autoOpenPlayer":true,"cursorSpeed":"NORMAL"}""")
        assertFalse(s.seeded)
        assertFalse(s.navHintShown)
    }
}
