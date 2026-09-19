package net.mrowser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test fun `a newer release compares greater`() {
        assertEquals(1, VersionCompare.compare("v1.4.0", "1.3.0"))
    }

    @Test fun `an older release compares smaller`() {
        assertEquals(-1, VersionCompare.compare("1.2.1", "1.3.0"))
    }

    @Test fun `the same version compares equal`() {
        assertEquals(0, VersionCompare.compare("1.3.0", "1.3.0"))
    }

    @Test fun `segments compare as numbers not strings`() {
        assertEquals(1, VersionCompare.compare("1.10.0", "1.9.0"))
    }

    @Test fun `the v prefix is optional on either side`() {
        assertEquals(0, VersionCompare.compare("v1.3.0", "1.3.0"))
        assertEquals(0, VersionCompare.compare("1.3.0", "V1.3.0"))
        assertEquals(0, VersionCompare.compare("v1.3.0", "v1.3.0"))
    }

    @Test fun `a missing segment counts as zero`() {
        assertEquals(0, VersionCompare.compare("1.4", "1.4.0"))
        assertEquals(1, VersionCompare.compare("1.4.1", "1.4"))
    }

    @Test fun `a non-numeric segment is unknown`() {
        assertNull(VersionCompare.compare("1.4.0-rc1", "1.3.0"))
        assertNull(VersionCompare.compare("1.3.0", "nightly"))
    }

    @Test fun `an empty version is unknown`() {
        assertNull(VersionCompare.compare("", "1.3.0"))
        assertNull(VersionCompare.compare("v", "1.3.0"))
    }

    @Test fun `isNewer is true only for a strictly greater version`() {
        assertTrue(VersionCompare.isNewer("1.4.0", "1.3.0"))
        assertFalse(VersionCompare.isNewer("1.3.0", "1.3.0"))
        assertFalse(VersionCompare.isNewer("1.2.1", "1.3.0"))
    }

    @Test fun `isNewer is false when either version is unknown`() {
        assertFalse(VersionCompare.isNewer("1.4.0-rc1", "1.3.0"))
        assertFalse(VersionCompare.isNewer("1.4.0", ""))
    }
}
