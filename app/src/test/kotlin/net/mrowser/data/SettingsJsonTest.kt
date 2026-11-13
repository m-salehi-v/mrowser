package net.mrowser.data

import net.mrowser.web.SearchEngine
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

    @Test fun `round trips the ad blocker fields`() {
        val s = Settings(blockAds = false, adsAllowedOn = setOf("site.example", "other.example"))
        assertEquals(s, SettingsJson.fromJson(SettingsJson.toJson(s)))
    }

    @Test fun `ad blocking is on and the allowlist empty when the fields are absent`() {
        val s = SettingsJson.fromJson("""{"autoOpenPlayer":true}""")
        assertTrue(s.blockAds)
        assertTrue(s.adsAllowedOn.isEmpty())
    }

    @Test fun `non-string allowlist entries are skipped`() {
        val s = SettingsJson.fromJson("""{"adsAllowedOn":["site.example", 5, null, "", "b.example"]}""")
        assertEquals(setOf("site.example", "b.example"), s.adsAllowedOn)
    }

    @Test fun `a malformed allowlist falls back to empty`() {
        assertTrue(SettingsJson.fromJson("""{"adsAllowedOn":"nope"}""").adsAllowedOn.isEmpty())
    }

    @Test fun `round trips the display-mode matching toggles`() {
        val s = Settings(matchFrameRate = true, matchResolution = true)
        assertEquals(s, SettingsJson.fromJson(SettingsJson.toJson(s)))
        val rateOnly = Settings(matchFrameRate = true)
        assertEquals(rateOnly, SettingsJson.fromJson(SettingsJson.toJson(rateOnly)))
    }

    @Test fun `display-mode matching is off by default`() {
        assertFalse(Settings().matchFrameRate)
        assertFalse(Settings().matchResolution)
    }

    @Test fun `display-mode matching is off for a pre-upgrade file`() {
        val s = SettingsJson.fromJson("""{"autoOpenPlayer":false,"blockAds":false,"seeded":true}""")
        assertFalse(s.matchFrameRate)
        assertFalse(s.matchResolution)
        assertFalse(s.autoOpenPlayer)
        assertTrue(s.seeded)
    }

    @Test fun `search engine round-trips by id`() {
        val s = Settings(searchEngine = SearchEngine.STARTPAGE)
        assertTrue(SettingsJson.toJson(s).contains("\"searchEngine\":\"startpage\""))
        assertEquals(SearchEngine.STARTPAGE, SettingsJson.fromJson(SettingsJson.toJson(s)).searchEngine)
    }

    @Test fun `a 1_4 settings file reads as duckduckgo`() =
        assertEquals(SearchEngine.DUCKDUCKGO, SettingsJson.fromJson("""{"autoOpenPlayer":false}""").searchEngine)

    @Test fun `an unknown engine id reads as duckduckgo`() =
        assertEquals(SearchEngine.DUCKDUCKGO, SettingsJson.fromJson("""{"searchEngine":"yahoo"}""").searchEngine)

    @Test fun `page settings round-trip`() {
        val s = Settings(desktopByDefault = true, desktopOverride = mapOf("a.example" to false), zoomBySite = mapOf("b.example" to 125))
        assertEquals(s, SettingsJson.fromJson(SettingsJson.toJson(s)))
    }

    @Test fun `a 1_4 settings file is mobile everywhere at 100 percent`() {
        val s = SettingsJson.fromJson("""{"autoOpenPlayer":true}""")
        assertFalse(s.desktopByDefault)
        assertEquals(emptyMap<String, Boolean>(), s.desktopOverride)
        assertEquals(emptyMap<String, Int>(), s.zoomBySite)
    }

    @Test fun `hand-edited zooms snap to a step and 100 is dropped`() = assertEquals(
        mapOf("a.example" to 125, "c.example" to 200),
        SettingsJson.fromJson("""{"zoomBySite":{"a.example":130,"b.example":100,"c.example":900,"d.example":"big"}}""").zoomBySite
    )

    @Test fun `non-boolean desktop overrides are skipped`() = assertEquals(
        mapOf("a.example" to true),
        SettingsJson.fromJson("""{"desktopOverride":{"a.example":true,"b.example":"yes"}}""").desktopOverride
    )
}
