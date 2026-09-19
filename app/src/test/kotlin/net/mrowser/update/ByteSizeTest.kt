package net.mrowser.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeTest {

    @Test fun `formats megabytes to one decimal`() {
        assertEquals("12.4 MB", ByteSize.format(12_400_000L))
    }

    @Test fun `rounds to one decimal`() {
        assertEquals("1.5 MB", ByteSize.format(1_460_000L))
    }

    @Test fun `a sub-megabyte size still reads in MB`() {
        assertEquals("0.2 MB", ByteSize.format(200_000L))
    }

    @Test fun `an unknown size is blank`() {
        assertEquals("", ByteSize.format(0L))
        assertEquals("", ByteSize.format(-1L))
    }
}
