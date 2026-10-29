package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The accent, as a value.
 *
 * It is parsed by hand so this test can exist at all: the colour decides the
 * ink on a customer's invoice, and a malformed preference has to degrade to a
 * blue document rather than crash the renderer that is in the middle of a
 * print job.
 */
class InvoiceDesignTest {

    @Test
    fun `the default accent parses to the expected colour`() {
        assertEquals(
            InvoiceDesign.DEFAULT_ACCENT_COLOR,
            InvoiceDesign(accent = InvoiceDesign.DEFAULT_ACCENT).accentColor,
        )
    }

    @Test
    fun `a colour without a hash parses the same`() {
        assertEquals(
            InvoiceDesign(accent = "#00696E").accentColor,
            InvoiceDesign(accent = "00696E").accentColor,
        )
    }

    @Test
    fun `a lower case colour parses the same`() {
        assertEquals(
            InvoiceDesign(accent = "#3F5B25").accentColor,
            InvoiceDesign(accent = "#3f5b25").accentColor,
        )
    }

    @Test
    fun `a malformed colour falls back instead of crashing`() {
        listOf("", "#", "blue", "#12345", "#GGGGGG", "#1234567").forEach { bad ->
            assertEquals(
                "accent '$bad'",
                InvoiceDesign.DEFAULT_ACCENT_COLOR,
                InvoiceDesign(accent = bad).accentColor,
            )
        }
    }

    @Test
    fun `the parsed colour is opaque`() {
        val colour = InvoiceDesign(accent = "#B3261E").accentColor

        assertEquals(0xFF, (colour ushr 24) and 0xFF)
    }

    @Test
    fun `every preset parses to its own colour`() {
        val parsed = InvoiceDesign.ACCENT_PRESETS.map { InvoiceDesign(accent = it).accentColor }

        assertEquals(
            "a preset is malformed and would print the fallback colour",
            parsed.size,
            parsed.distinct().size,
        )
        assertNotEquals(0, parsed.first())
    }

    @Test
    fun `a preset never falls back to the default by accident`() {
        InvoiceDesign.ACCENT_PRESETS.forEach { hex ->
            val colour = InvoiceDesign(accent = hex).accentColor
            val expected = (0xFF000000L or hex.removePrefix("#").toLong(16)).toInt()

            assertEquals(hex, expected, colour)
        }
    }

    @Test
    fun `the print switches default to on`() {
        val design = InvoiceDesign.default()

        assertTrue(design.logoEnabled)
        assertTrue(design.showAmountWords)
        assertTrue(design.showLegalIdentifiers)
        assertTrue(design.showPaymentMethod)
        assertTrue(design.showFooter)
        assertEquals(InvoiceTemplate.CLASSIQUE, design.template)
    }

    @Test
    fun `only the receipt is a roll`() {
        assertEquals(
            listOf(InvoiceTemplate.TICKET),
            InvoiceTemplate.entries.filter { it.isRoll },
        )
    }
}
