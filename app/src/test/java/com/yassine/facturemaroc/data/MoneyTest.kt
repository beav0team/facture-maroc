package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    private val narrow = '\u202F'

    @Test
    fun `a whole amount keeps its two decimals`() {
        // The old formatter collapsed this to "3432 DH" on the list while the
        // PDF printed "3 432,00 DH" for the same invoice.
        assertEquals("3${narrow}432,00 DH", Money.dirhams(3432.0))
        assertEquals("0,00 DH", Money.dirhams(0.0))
    }

    @Test
    fun `thousands are grouped with a narrow no-break space`() {
        assertEquals("1${narrow}812,60 DH", Money.dirhams(1812.6))
        assertEquals("12${narrow}345,67 DH", Money.dirhams(12_345.67))
        assertEquals("1${narrow}234${narrow}567,89 DH", Money.dirhams(1_234_567.89))
    }

    @Test
    fun `cents are rounded, never truncated`() {
        assertEquals("83,33 DH", Money.dirhams(83.325))
        assertEquals("1${narrow}000,00 DH", Money.dirhams(999.999))
    }

    @Test
    fun `plain amounts carry no currency`() {
        assertEquals("1${narrow}812,60", Money.plain(1812.6))
    }

    @Test
    fun `a sign is only written when the amount is negative`() {
        assertEquals("812,60", Money.signedPlain(812.6))
        assertEquals("\u2212812,60", Money.signedPlain(-812.6))
    }

    @Test
    fun `an unusable amount prints as zero rather than crashing a document`() {
        assertEquals("0,00 DH", Money.dirhams(Double.NaN))
        assertEquals("0,00 DH", Money.dirhams(Double.POSITIVE_INFINITY))
    }
}
