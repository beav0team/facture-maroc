package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reading a price the way a Moroccan shop quotes it.
 *
 * The rule under test is the one a shopkeeper checks in his head: a price typed
 * TTC must come back as the same price on the document, and the HT + TVA lines
 * must add up to it.
 */
class PriceBasisTest {

    private fun round(value: Double): Double = Math.round(value * 100.0) / 100.0

    @Test
    fun `a TTC price is divided by the tax it contains`() {
        assertEquals(125.0, PriceBasis.excludingVat(150.0, 20.0), 0.000001)
        assertEquals(100.0, PriceBasis.excludingVat(120.0, 20.0), 0.000001)
        // 14 % is the rate on some services; the arithmetic must not assume 20.
        assertEquals(87.71929824561403, PriceBasis.excludingVat(100.0, 14.0), 0.000001)
    }

    @Test
    fun `an exempt price is already its own HT price`() {
        assertEquals(150.0, PriceBasis.excludingVat(150.0, 0.0), 0.0)
        assertEquals(150.0, PriceBasis.includingVat(150.0, 0.0), 0.0)
    }

    @Test
    fun `the unit price is not rounded away from the price that was quoted`() {
        // 99,99 TTC at 20 % is 83,325 HT. Rounding it to 83,33 would make the
        // invoice total 100,00 — a centime more than the customer was told.
        val ht = PriceBasis.excludingVat(99.99, 20.0)

        assertEquals(83.325, ht, 0.000001)
        assertEquals(99.99, PriceBasis.includingVat(ht, 20.0), 0.000001)
    }

    @Test
    fun `a line entered TTC comes back as the price the shop quoted`() {
        val line = InvoiceLine("Montage", quantity = 1, unitPrice = PriceBasis.excludingVat(150.0, 20.0))
        val data = InvoiceData(
            number = "F-1",
            date = "29/10/2026",
            client = InvoiceClient("Client de passage"),
            lines = listOf(line),
        )

        assertEquals(125.0, data.totalExcludingVat, 0.000001)
        assertEquals(25.0, data.vatAmount, 0.000001)
        assertEquals(150.0, data.total, 0.000001)
        assertEquals(125.0, data.vatBase, 0.000001)
    }

    @Test
    fun `a tricky price survives the round trip to the total`() {
        val line = InvoiceLine("Pose", quantity = 1, unitPrice = PriceBasis.excludingVat(99.99, 20.0))
        val data = InvoiceData(
            number = "F-1",
            date = "29/10/2026",
            client = InvoiceClient("Client de passage"),
            lines = listOf(line),
        )

        // What the shop said, and what the customer pays.
        assertEquals(99.99, data.total, 0.000001)
        assertEquals(round(data.totalExcludingVat + data.vatAmount), data.total, 0.000001)
    }

    @Test
    fun `three units of a TTC price still add up`() {
        val line = InvoiceLine("Montage", quantity = 3, unitPrice = PriceBasis.excludingVat(150.0, 20.0))
        val data = InvoiceData(
            number = "F-1",
            date = "29/10/2026",
            client = InvoiceClient("Client de passage"),
            lines = listOf(line),
        )

        assertEquals(375.0, data.totalExcludingVat, 0.000001)
        assertEquals(75.0, data.vatAmount, 0.000001)
        assertEquals(450.0, data.total, 0.000001)
    }

    @Test
    fun `an exempt line in a TTC invoice keeps its price and charges nothing`() {
        val taxed = InvoiceLine("Pose", quantity = 1, unitPrice = PriceBasis.excludingVat(120.0, 20.0))
        val exempt = InvoiceLine("Export", quantity = 1, unitPrice = 100.0, taxExempt = true)
        val data = InvoiceData(
            number = "F-1",
            date = "29/10/2026",
            client = InvoiceClient("Client de passage"),
            lines = listOf(taxed, exempt),
        )

        assertEquals(200.0, data.totalExcludingVat, 0.000001)
        assertEquals(20.0, data.vatAmount, 0.000001)
        // 100 TTC for the taxed line + 100 HT for the exempt one.
        assertEquals(220.0, data.total, 0.000001)
    }

    @Test
    fun `an auto-entrepreneur divides by nothing`() {
        val data = InvoiceData(
            number = "F-1",
            date = "29/10/2026",
            client = InvoiceClient("Client de passage"),
            lines = listOf(InvoiceLine("Pose", quantity = 1, unitPrice = 150.0)),
            vatNotApplicable = true,
        )

        // The shop typed 150 and no tax is inside it, so 150 is the total.
        assertEquals(150.0, data.total, 0.000001)
        assertEquals(0.0, data.vatAmount, 0.0)
    }
}
