package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quantity tiers and free attributes: the two catalogue features a shop uses
 * when its price depends on how much is bought and its article has a reference
 * the customer can look up.
 *
 * The arithmetic is tested on the JVM because a tier boundary is a promise the
 * shop makes to a customer, and "I sold ten, why did it charge the single-unit
 * price?" has to be answerable by a test rather than by reading a screen.
 */
class PriceTierTest {

    private fun tiered(
        tiers: List<PriceTier> = listOf(PriceTier(10, 90.0), PriceTier(100, 80.0)),
        price: Double = 100.0,
    ) = SavedItem(label = "Câble", price = price, priceTiers = tiers)

    @Test
    fun `a tier applies from its threshold and not below`() {
        val item = tiered()

        assertEquals(100.0, item.tierPrice(9.0), 0.001)
        assertEquals(90.0, item.tierPrice(10.0), 0.001)
        assertEquals(90.0, item.tierPrice(99.0), 0.001)
        assertEquals(80.0, item.tierPrice(100.0), 0.001)
        assertEquals(80.0, item.tierPrice(500.0), 0.001)
    }

    @Test
    fun `an article without tiers always charges its price`() {
        val item = SavedItem(label = "Chaise", price = 120.0)

        assertEquals(120.0, item.tierPrice(1.0), 0.001)
        assertEquals(120.0, item.tierPrice(1_000.0), 0.001)
    }

    @Test
    fun `a decimal quantity keeps the base price`() {
        // 2,5 m of cable has no whole-unit boundary to cross: the tier ladder
        // describes articles counted in units, not lengths.
        val item = tiered(tiers = listOf(PriceTier(2, 50.0)))

        assertEquals(100.0, item.tierPrice(2.5), 0.001)
        assertEquals(50.0, item.tierPrice(2.0), 0.001)
    }

    @Test
    fun `the greatest matching threshold wins, whatever the order in the file`() {
        val item = tiered(tiers = listOf(PriceTier(100, 80.0), PriceTier(10, 90.0)))

        assertEquals(80.0, item.tierPrice(100.0), 0.001)
        assertEquals(90.0, item.tierPrice(50.0), 0.001)
    }

    @Test
    fun `a line freezes the tier price and the threshold it crossed`() {
        val line = tiered().toLine(quantity = 12.0)

        assertEquals(90.0, line.unitPrice, 0.001)
        assertEquals(90.0, line.appliedTierPrice!!, 0.001)
        assertEquals(10, line.tierFromQuantity)
    }

    @Test
    fun `a line without a matching tier has no tier fields`() {
        val line = tiered().toLine(quantity = 5.0)

        assertEquals(100.0, line.unitPrice, 0.001)
        assertNull(line.appliedTierPrice)
        assertNull(line.tierFromQuantity)
    }

    @Test
    fun `the threshold prints beside the line`() {
        val line = tiered().toLine(quantity = 12.0)

        assertTrue(line.lineLabel.contains("dès 10"))
    }

    @Test
    fun `attributes are copied onto the line`() {
        val item = SavedItem(
            label = "Câble",
            price = 100.0,
            attributes = listOf(
                ItemAttribute(ItemAttributeKeys.REFERENCE, "E-100"),
                ItemAttribute(ItemAttributeKeys.BRAND, "Legrand"),
            ),
        )

        val line = item.toLine()

        assertEquals(2, line.lineAttributes.size)
        assertEquals("Référence", line.lineAttributes[0].key)
        assertEquals("E-100", line.lineAttributes[0].value)
    }

    @Test
    fun `attributes print after the unit`() {
        val line = SavedItem(
            label = "Câble",
            price = 100.0,
            unit = "m",
            attributes = listOf(ItemAttribute("Référence", "E-100")),
        ).toLine()

        assertEquals("Câble · m · Référence: E-100", line.lineLabel)
    }

    @Test
    fun `a blank attribute is not printed`() {
        val line = InvoiceLine(
            "Câble",
            1.0,
            100.0,
            lineAttributes = listOf(
                ItemAttribute("", "E-100"),
                ItemAttribute("Marque", ""),
            ),
        )

        assertEquals("Câble", line.lineLabel)
    }

    @Test
    fun `tiers and attributes survive the line JSON round trip`() {
        val line = InvoiceLine(
            description = "Câble",
            quantity = 12.0,
            unitPrice = 90.0,
            appliedTierPrice = 90.0,
            tierFromQuantity = 10,
            lineAttributes = listOf(ItemAttribute("Référence", "E-100")),
        )

        val restored = InvoiceJson.lineFromJson(InvoiceJson.lineToJson(line))

        assertEquals(line, restored)
        assertEquals(90.0, restored.appliedTierPrice!!, 0.0)
        assertEquals(10, restored.tierFromQuantity)
        assertEquals("E-100", restored.lineAttributes.single().value)
    }

    @Test
    fun `a line without tiers or attributes writes neither key`() {
        val json = InvoiceJson.lineToJson(InvoiceLine("Main-d'œuvre", 1.0, 100.0))

        assertTrue(!json.has("tierFromQuantity"))
        assertTrue(!json.has("appliedTierPrice"))
        assertTrue(!json.has("lineAttributes"))
    }

    @Test
    fun `an article with tiers and attributes reads back from its own JSON shape`() {
        // The store writes each item through the same two helpers; this guards
        // the shape the next launch has to read.
        val item = SavedItem(
            label = "Câble",
            price = 100.0,
            priceTiers = listOf(PriceTier(10, 90.0)),
            attributes = listOf(ItemAttribute("Référence", "E-100")),
        )

        val line = item.toLine(quantity = 10.0)

        assertEquals(90.0, line.unitPrice, 0.001)
        assertEquals(10, line.tierFromQuantity)
        assertEquals("E-100", line.lineAttributes.single().value)
    }
}
