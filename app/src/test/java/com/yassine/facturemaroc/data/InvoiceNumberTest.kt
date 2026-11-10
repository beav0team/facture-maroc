package com.yassine.facturemaroc.data

import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The printed shape of a document number and the arithmetic of the annual
 * reset. Both are pure, so they are pinned here rather than on a device.
 */
class InvoiceNumberTest {

    private fun date(year: Int, month: Int, day: Int) =
        GregorianCalendar(year, month, day).time

    private val sample = date(2026, Calendar.NOVEMBER, 10)

    @Test
    fun padsTheSequenceToFourDigits() {
        assertEquals("F-20261110-0001", InvoiceNumber.format("F", sample, 1))
        assertEquals("F-20261110-0042", InvoiceNumber.format("F", sample, 42))
    }

    @Test
    fun keepsAValueLongerThanFourDigits() {
        assertEquals("F-20261110-12345", InvoiceNumber.format("F", sample, 12345))
    }

    @Test
    fun usesAShopPrefix() {
        assertEquals("INV-20261110-0007", InvoiceNumber.format("INV", sample, 7))
    }

    @Test
    fun doesNotDoubleADashThePrefixAlreadyCarries() {
        assertEquals("INV-20261110-0007", InvoiceNumber.format("INV-", sample, 7))
        assertEquals("INV-20261110-0007", InvoiceNumber.format("  INV--  ", sample, 7))
    }

    @Test
    fun aBlankPrefixPrintsNoDanglingDash() {
        assertEquals("20261110-0001", InvoiceNumber.format("", sample, 1))
        assertEquals("20261110-0001", InvoiceNumber.format("  -  ", sample, 1))
    }

    @Test
    fun neverPrintsASequenceBelowOne() {
        assertEquals("F-20261110-0001", InvoiceNumber.format("F", sample, 0))
        assertEquals("F-20261110-0001", InvoiceNumber.format("F", sample, -5))
    }

    @Test
    fun annualResetRestartsACounterFromAnotherYear() {
        assertEquals(1, InvoiceNumber.effectiveNext(42, 2025, 2026, resetAnnually = true))
    }

    @Test
    fun annualResetLeavesThisYearsCounterAlone() {
        assertEquals(42, InvoiceNumber.effectiveNext(42, 2026, 2026, resetAnnually = true))
    }

    @Test
    fun withoutAnnualResetTheCounterKeepsCounting() {
        assertEquals(42, InvoiceNumber.effectiveNext(42, 2025, 2026, resetAnnually = false))
    }

    @Test
    fun anUnstampedCounterIsNotMistakenForLastYears() {
        // A file written before the year was stored has 0, not 2025: keeping
        // it is the only reading that cannot reissue a number the shop used.
        assertEquals(42, InvoiceNumber.effectiveNext(42, 0, 2026, resetAnnually = true))
    }

    @Test
    fun aFreshCounterStartsAtOne() {
        assertEquals(1, InvoiceNumber.effectiveNext(0, 2026, 2026, resetAnnually = true))
    }

    @Test
    fun yearRolloverChangesTheDatePartAndTheResetDecision() {
        val december = date(2025, Calendar.DECEMBER, 31)
        val january = date(2026, Calendar.JANUARY, 1)
        assertEquals(2025, InvoiceNumber.yearOf(december))
        assertEquals(2026, InvoiceNumber.yearOf(january))
        assertEquals("F-20251231-0042", InvoiceNumber.format("F", december, 42))
        assertEquals(
            1,
            InvoiceNumber.effectiveNext(
                stored = 42,
                storedYear = InvoiceNumber.yearOf(december),
                currentYear = InvoiceNumber.yearOf(january),
                resetAnnually = true,
            ),
        )
    }

    @Test
    fun aPrefixMustStillBeAFilename() {
        assertTrue(NumberingStore.isValidPrefix("F"))
        assertTrue(NumberingStore.isValidPrefix("FA-2026_1"))
        assertTrue(NumberingStore.isValidPrefix("INV"))
        assertFalse(NumberingStore.isValidPrefix(""))
        assertFalse(NumberingStore.isValidPrefix("F 1"))
        assertFalse(NumberingStore.isValidPrefix("F/1"))
        assertFalse(NumberingStore.isValidPrefix("F.1"))
        assertFalse(NumberingStore.isValidPrefix("F#1"))
    }
}
