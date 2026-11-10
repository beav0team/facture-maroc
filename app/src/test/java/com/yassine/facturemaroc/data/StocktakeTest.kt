package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stocktake arithmetic: counted against expected, and the difference it
 * writes.
 */
class StocktakeTest {

    private fun item(id: String, label: String, stock: Int, purchase: Double = 10.0) =
        SavedItem(label = label, price = 100.0, purchasePrice = purchase, stock = stock, id = id)

    private val items = listOf(
        item("chair", "Chaise", stock = 5),
        item("cushion", "Coussin", stock = 2, purchase = 4.0),
        SavedItem(label = "Montage", price = 50.0, id = "labour"),
    )

    @Test
    fun `a counted quantity becomes a difference against the ledger`() {
        val rows = Stocktake.rows(items, mapOf("chair" to 3, "cushion" to 2)) { it.stock ?: 0 }

        assertEquals(2, rows.size)
        assertEquals(-2, rows.first { it.itemId == "chair" }.difference)
        assertEquals(0, rows.first { it.itemId == "cushion" }.difference)
    }

    @Test
    fun `an article left untouched keeps the expected number, it is not counted zero`() {
        val rows = Stocktake.rows(items, emptyMap()) { it.stock ?: 0 }

        assertTrue(rows.all { it.difference == 0 })
        assertEquals(2, rows.first { it.itemId == "cushion" }.counted)
    }

    @Test
    fun `the expectation comes from the supplied balance, not the cache`() {
        val rows = Stocktake.rows(items, mapOf("chair" to 4)) { 7 }

        assertEquals(7, rows.first { it.itemId == "chair" }.expected)
        assertEquals(-3, rows.first { it.itemId == "chair" }.difference)
    }

    @Test
    fun `only the articles that changed produce an entry`() {
        val rows = Stocktake.rows(items, mapOf("chair" to 6, "cushion" to 2)) { it.stock ?: 0 }

        val entries = Stocktake.entries(rows, reason = "Casse", reference = "Inventaire du 10/11/2026")

        assertEquals(1, entries.size)
        assertEquals("chair", entries.single().itemId)
        assertEquals(1, entries.single().delta)
        assertEquals(StockReason.STOCKTAKE, entries.single().reason)
        assertEquals("Casse", entries.single().note)
        assertEquals("Inventaire du 10/11/2026", entries.single().reference)
    }

    @Test
    fun `a shortage is a negative entry, never hidden`() {
        val rows = Stocktake.rows(items, mapOf("cushion" to 0)) { it.stock ?: 0 }

        val entry = Stocktake.entries(rows, "Vol", "Inventaire").single()

        assertEquals(-2, entry.delta)
        assertEquals(-8.0, rows.first { it.itemId == "cushion" }.differenceValue, 0.001)
    }

    @Test
    fun `the summary totals units and value`() {
        val rows = Stocktake.rows(
            items,
            mapOf("chair" to 3, "cushion" to 5),
        ) { it.stock ?: 0 }

        val summary = Stocktake.summary(rows)

        assertEquals(2, summary.rows)
        assertEquals(2, summary.adjusted)
        assertEquals(7, summary.expectedUnits)
        assertEquals(8, summary.countedUnits)
        assertEquals(1, summary.differenceUnits)
        // -2 chairs at 10 DH + 3 cushions at 4 DH.
        assertEquals(-8.0, summary.differenceValue, 0.001)
    }

    @Test
    fun `a service is never on the sheet`() {
        val rows = Stocktake.rows(items, mapOf("labour" to 99)) { it.stock ?: 0 }

        assertTrue(rows.none { it.itemId == "labour" })
    }

    @Test
    fun `the applied entries leave the ledger equal to the counted quantity`() {
        val ledger = StockLedger(java.io.File.createTempFile("stocktake", ".jsonl"))
        ledger.seed(items)
        val rows = Stocktake.rows(items, mapOf("chair" to 1, "cushion" to 8)) { ledger.balance(it.id) }

        ledger.appendAll(Stocktake.entries(rows, "Inventaire", "Inventaire du 10/11/2026"))

        assertEquals(1, ledger.balance("chair"))
        assertEquals(8, ledger.balance("cushion"))
    }
}
