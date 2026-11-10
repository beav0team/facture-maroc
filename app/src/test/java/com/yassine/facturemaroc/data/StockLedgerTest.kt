package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The stock ledger: signed arithmetic, cache versus history, and a file that can
 * be torn by a phone dying.
 */
class StockLedgerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun ledger(name: String = "stock_ledger.jsonl"): StockLedger =
        StockLedger(File(temp.root, name))

    private fun item(id: String, stock: Int?, label: String = id) =
        SavedItem(label = label, price = 100.0, stock = stock, reorderThreshold = null, id = id)

    @Test
    fun `a sale, a credit, a purchase and a count add up`() {
        val ledger = ledger()
        ledger.appendAll(
            listOf(
                StockEntry("chair", -3, StockReason.INVOICE, reference = "F-1"),
                StockEntry("chair", +1, StockReason.CREDIT, reference = "A-1"),
                StockEntry("chair", +10, StockReason.PURCHASE, reference = "BL-9"),
                StockEntry("chair", -2, StockReason.STOCKTAKE, note = "Casse"),
            ),
        )

        assertEquals(6, ledger.balance("chair"))
        assertEquals(4, ledger.entriesFor("chair").size)
    }

    @Test
    fun `the history reads newest first`() {
        val ledger = ledger()
        ledger.append(StockEntry("chair", 1, StockReason.PURCHASE, at = 100L))
        ledger.append(StockEntry("chair", -1, StockReason.INVOICE, at = 200L))

        assertEquals(
            listOf(StockReason.INVOICE, StockReason.PURCHASE),
            ledger.entriesFor("chair").map { it.reason },
        )
    }

    @Test
    fun `negative stock is kept and flagged, never floored at zero`() {
        val ledger = ledger()
        ledger.append(StockEntry("chair", -5, StockReason.INVOICE))

        val rebuilt = ledger.rebuild(listOf(item("chair", stock = 0))).single()

        assertEquals(-5, rebuilt.stock)
        assertTrue(rebuilt.outOfStock)
    }

    @Test
    fun `a cached quantity that disagrees is reported and can be rebuilt`() {
        val ledger = ledger()
        ledger.append(StockEntry("chair", +7, StockReason.PURCHASE))
        val cached = listOf(item("chair", stock = 3), item("cushion", stock = 0))

        val drift = ledger.drift(cached)

        assertEquals(listOf("chair"), drift.map { it.item.id })
        assertEquals(3, drift.single().cached)
        assertEquals(7, drift.single().ledger)
        assertEquals(7, ledger.rebuild(cached).first { it.id == "chair" }.stock)
    }

    @Test
    fun `a service keeps no quantity through a rebuild`() {
        val ledger = ledger()
        ledger.append(StockEntry("labour", +4, StockReason.ADJUSTMENT))

        val rebuilt = ledger.rebuild(listOf(item("labour", stock = null))).single()

        assertEquals(null, rebuilt.stock)
    }

    @Test
    fun `a truncated last line is ignored, not fatal`() {
        val file = File(temp.root, "torn.jsonl")
        file.writeText(
            StockEntry("chair", -1, StockReason.INVOICE).toLine() + "\n" +
                StockEntry("chair", -2, StockReason.INVOICE).toLine() + "\n" +
                "{\"itemId\":\"chair\",\"delta\":-3",
        )

        val ledger = StockLedger(file)

        assertEquals(-3, ledger.balance("chair"))
        assertEquals(2, ledger.all().size)
    }

    @Test
    fun `an unknown reason reads as an adjustment instead of losing the line`() {
        val entry = StockEntry.parse(
            """{"itemId":"chair","delta":-1,"reason":"future","at":1}""",
        )

        assertEquals(StockReason.ADJUSTMENT, entry?.reason)
        assertEquals(-1, entry?.delta)
    }

    @Test
    fun `entries survive a reopen`() {
        val file = File(temp.root, "persisted.jsonl")
        StockLedger(file).append(StockEntry("chair", +3, StockReason.PURCHASE, reference = "BL-1"))

        assertEquals(3, StockLedger(file).balance("chair"))
    }

    @Test
    fun `seeding writes opening quantities once`() {
        val ledger = ledger()
        val items = listOf(item("chair", stock = 4), item("cushion", stock = 0))

        assertEquals(1, ledger.seed(items))
        assertEquals(0, ledger.seed(items))
        assertEquals(4, ledger.balance("chair"))
        assertEquals(StockReason.ADJUSTMENT, ledger.entriesFor("chair").single().reason)
    }

    @Test
    fun `a failed write does not enter the in-memory ledger`() {
        // A directory cannot be appended to as a file, which is how the test
        // reaches the failure branch without mocking the file system.
        val ledger = StockLedger(temp.root)

        assertFalse(ledger.append(StockEntry("chair", -1, StockReason.INVOICE)))
        assertEquals(0, ledger.balance("chair"))
    }
}
