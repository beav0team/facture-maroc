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
    fun `three TTC lines printed one by one add up to the printed subtotal`() {
        // 99,99 TTC three times: each line prints 83,33, and three of those make
        // 249,99 — not the 249,98 the unrounded sum rounds to. A document whose
        // own column does not add up is a document a customer can query.
        val line = InvoiceLine("Pose", quantity = 1, unitPrice = PriceBasis.excludingVat(99.99, 20.0))
        val data = InvoiceData(
            number = "F-1",
            date = "29/10/2026",
            client = InvoiceClient("Client de passage"),
            lines = listOf(line, line, line),
        )

        assertEquals(249.99, data.subtotal, 0.000001)
        assertEquals(round(data.lines.sumOf { round(it.total) }), data.subtotal, 0.000001)
        // The customer still pays the price the shop quoted, three times over.
        assertEquals(299.97, data.total, 0.000001)
        // And HT + TVA is still the TTC, to the centime.
        assertEquals(round(data.totalExcludingVat + data.vatAmount), data.total, 0.000001)
    }

    @Test
    fun `a TTC line is re-read when the buyer changes`() {
        // What the invoice stores when the shop quotes TTC: the number typed,
        // and the HT it works out to for the buyer in front of it.
        val typed = 150.0
        val line = InvoiceLine(
            description = "Montage",
            quantity = 1,
            unitPrice = PriceBasis.excludingVat(typed, 20.0),
            ttcPrice = typed,
        )

        val taxed = PriceBasis.repriceTtcLines(listOf(line), 20.0).single()
        assertEquals(125.0, taxed.unitPrice, 0.000001)

        // The same line for an exempt client: 150 with no tax inside it, which
        // is the number the shop typed and the customer was promised.
        val exempt = PriceBasis.repriceTtcLines(listOf(line), 0.0).single()
        assertEquals(150.0, exempt.unitPrice, 0.000001)
    }

    @Test
    fun `an HT line never moves when the buyer changes`() {
        val line = InvoiceLine("Pneu", quantity = 1, unitPrice = 125.0)

        assertEquals(125.0, PriceBasis.repriceTtcLines(listOf(line), 0.0).single().unitPrice, 0.0)
        assertEquals(125.0, PriceBasis.repriceTtcLines(listOf(line), 20.0).single().unitPrice, 0.0)
    }

    @Test
    fun `an exempt line is never re-read`() {
        // An exempt line has no tax inside its price, so there is nothing to
        // take out of it whatever the client pays on the rest of the invoice.
        val line = InvoiceLine("Export", quantity = 1, unitPrice = 100.0, taxExempt = true)

        assertEquals(100.0, PriceBasis.repriceTtcLines(listOf(line), 20.0).single().unitPrice, 0.0)
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
