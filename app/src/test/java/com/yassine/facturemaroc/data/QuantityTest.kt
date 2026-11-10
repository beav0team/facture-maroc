package com.yassine.facturemaroc.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Quantities sold by the metre, the kilo or the hour.
 *
 * The line arithmetic, the printed form, the JSON round trip and the way stock
 * (counted in whole articles) absorbs a decimal quantity.
 */
class QuantityTest {

    private fun data(vararg lines: InvoiceLine, type: DocumentType = DocumentType.FACTURE) =
        InvoiceData(
            number = "F-1",
            date = "10/11/2026",
            client = InvoiceClient("Client de passage"),
            lines = lines.toList(),
            vatPercent = 0.0,
            documentType = type,
        )

    @Test
    fun `a decimal quantity is multiplied exactly`() {
        val line = InvoiceLine("Câble", quantity = 2.5, unitPrice = 40.0)

        assertEquals(100.0, line.total, 0.0001)
    }

    @Test
    fun `a decimal line total is rounded to the centime`() {
        val line = InvoiceLine("Câble au mètre", quantity = 1.5, unitPrice = 99.99)

        // 1,5 × 99,99 = 149,985: the page must not print a third decimal.
        // The double is a hair below 149,985, so the centime rounds down —
        // which is the same money() every other amount in the app goes through.
        assertEquals(149.98, data(line).subtotal, 0.0001)
        assertEquals(149.98, data(line).total, 0.0001)
    }

    @Test
    fun `a decimal discount keeps the line arithmetic`() {
        val line = InvoiceLine(
            "Tissu",
            quantity = 2.5,
            unitPrice = 100.0,
            lineDiscountPercent = 10,
        )

        assertEquals(225.0, line.total, 0.0001)
    }

    @Test
    fun `quantities print without trailing zeros`() {
        assertEquals("2", quantityText(2.0))
        assertEquals("1,5", quantityText(1.5))
        assertEquals("0,25", quantityText(0.25))
        assertEquals("12", quantityText(12.0))
        assertEquals("0,125", quantityText(0.125))
    }

    @Test
    fun `the machine form keeps a dot`() {
        assertEquals("2", quantityValue(2.0))
        assertEquals("1.5", quantityValue(1.5))
        assertEquals("0.25", quantityValue(0.25))
    }

    @Test
    fun `a quantity is not printed with floating point noise`() {
        // 0,1 + 0,2 is 0,30000000000000004 in binary; the paper says 0,3.
        assertEquals("0,3", quantityText(0.1 + 0.2))
    }

    @Test
    fun `an integer quantity in an old file reads as a decimal`() {
        val json = JSONObject()
            .put("description", "Chaise")
            .put("quantity", 3)
            .put("unitPrice", 100.0)

        val line = InvoiceJson.lineFromJson(json)

        assertEquals(3.0, line.quantity, 0.0)
    }

    @Test
    fun `a decimal quantity survives the json round trip`() {
        val line = InvoiceLine("Câble", quantity = 2.5, unitPrice = 40.0, unit = "m")

        assertEquals(line, InvoiceJson.lineFromJson(InvoiceJson.lineToJson(line)))
    }

    @Test
    fun `a file with a half quantity keeps it`() {
        val json = JSONObject()
            .put("description", "Main-d'œuvre")
            .put("quantity", 0.5)
            .put("unitPrice", 100.0)

        assertEquals(0.5, InvoiceJson.lineFromJson(json).quantity, 0.0)
    }

    @Test
    fun `stock rounds a decimal quantity to whole articles`() {
        val items = listOf(SavedItem(label = "Câble", price = 40.0, stock = 10))

        // 2,5 m of cable is still three articles off the shelf's ledger
        // (Math.round rounds half up), not two.
        val after = StockMovement.apply(items, data(InvoiceLine("Câble", 2.5, 40.0)))

        assertEquals(7, after.single().stock)
    }

    @Test
    fun `a quantity below half a unit moves no stock`() {
        val items = listOf(SavedItem(label = "Fil", price = 10.0, stock = 10))

        val after = StockMovement.apply(items, data(InvoiceLine("Fil", 0.4, 10.0)))

        assertEquals(10, after.single().stock)
    }

    @Test
    fun `a credit note puts the rounded quantity back`() {
        val items = listOf(SavedItem(label = "Câble", price = 40.0, stock = 7))

        val after = StockMovement.apply(
            items,
            data(InvoiceLine("Câble", 2.5, 40.0), type = DocumentType.AVOIR),
        )

        assertEquals(10, after.single().stock)
    }
}
