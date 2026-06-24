package net.mrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsJsonTest {

    @Test fun `round trips all fields`() {
        val s = Settings(autoOpenPlayer = false, cursorSpeed = CursorSpeed.FAST)
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
        val s = SettingsJson.fromJson("""{"autoOpenPlayer":true,"cursorSpeed":"WARP"}""")
        assertEquals(CursorSpeed.NORMAL, s.cursorSpeed)
    }
}
