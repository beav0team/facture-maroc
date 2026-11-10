package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The two costing methods over the same history, and the shapes that used to
 * divide by zero.
 */
class StockCostTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun buy(quantity: Int, cost: Double, at: Long = 0L) =
        StockEntry("chair", quantity, StockReason.PURCHASE, at = at, unitCost = cost)

    private fun sell(quantity: Int, at: Long = 0L) =
        StockEntry("chair", -quantity, StockReason.INVOICE, at = at)

    @Test
    fun `CMP averages two purchases and sells at the running average`() {
        val history = listOf(
            buy(10, 100.0),
            buy(10, 200.0),
            sell(5),
        )

        val valuation = StockCost.valuation(history, CostMethod.CMP)

        assertEquals(15, valuation.quantity)
        assertEquals(150.0, valuation.unitCost ?: 0.0, 0.001)
        assertEquals(2250.0, valuation.value, 0.001)
        assertFalse(valuation.negative)
    }

    @Test
    fun `FIFO consumes the three layers oldest first`() {
        val history = listOf(
            buy(10, 100.0),
            buy(10, 200.0),
            buy(10, 300.0),
            sell(15),
        )

        val valuation = StockCost.valuation(history, CostMethod.FIFO)

        // Five left of the 200 layer and all ten of the 300 layer.
        assertEquals(15, valuation.quantity)
        assertEquals(4000.0, valuation.value, 0.001)
        // 4 000 / 15 — the average of what actually remains, not of what came in.
        assertEquals(266.67, valuation.unitCost ?: 0.0, 0.01)
    }

    @Test
    fun `the two methods disagree on the same history`() {
        val history = listOf(buy(10, 100.0), buy(10, 200.0), sell(10))

        val cmp = StockCost.valuation(history, CostMethod.CMP)
        val fifo = StockCost.valuation(history, CostMethod.FIFO)

        assertEquals(150.0, cmp.unitCost ?: 0.0, 0.001)
        assertEquals(200.0, fifo.unitCost ?: 0.0, 0.001)
    }

    @Test
    fun `switching the method does not rewrite the history`() {
        val history = listOf(buy(2, 10.0), sell(1))
        val before = history.map { it.toLine() }

        StockCost.valuation(history, CostMethod.CMP)
        StockCost.valuation(history, CostMethod.FIFO)

        assertEquals(before, history.map { it.toLine() })
    }

    @Test
    fun `an empty ledger has no cost and no NaN`() {
        val valuation = StockCost.valuation(emptyList(), CostMethod.CMP)

        assertEquals(0, valuation.quantity)
        assertNull(valuation.unitCost)
        assertEquals(0.0, valuation.value, 0.0)
        assertFalse(valuation.negative)
        assertNull(StockCost.unitCost(emptyList(), CostMethod.FIFO))
    }

    @Test
    fun `a negative shelf keeps the last cost and is flagged`() {
        val history = listOf(buy(5, 100.0), sell(8))

        val valuation = StockCost.valuation(history, CostMethod.CMP)

        assertEquals(-3, valuation.quantity)
        assertEquals(100.0, valuation.unitCost ?: 0.0, 0.001)
        assertTrue(valuation.negative)
        assertEquals(-300.0, valuation.value, 0.001)
    }

    @Test
    fun `without costing the quantity is still counted`() {
        val valuation = StockCost.valuation(listOf(buy(4, 50.0), sell(1)), CostMethod.NONE)

        assertEquals(3, valuation.quantity)
        assertNull(valuation.unitCost)
        assertEquals(0.0, valuation.value, 0.0)
    }

    @Test
    fun `an inbound with no price leaves the average alone`() {
        val history = listOf(
            buy(10, 100.0),
            // A delivery whose price nobody typed: the goods are real, the
            // cost is not known, and the average must not be diluted to zero.
            StockEntry("chair", 10, StockReason.PURCHASE, unitCost = 0.0),
            sell(5),
        )

        val valuation = StockCost.valuation(history, CostMethod.CMP)

        assertEquals(15, valuation.quantity)
        assertEquals(100.0, valuation.unitCost ?: 0.0, 0.001)
    }

    @Test
    fun `a history with no price at all has no cost`() {
        val valution = StockCost.valuation(listOf(buy(3, 0.0)), CostMethod.CMP)
        assertNull(valution.unitCost)

        val fifo = StockCost.valuation(listOf(buy(3, 0.0)), CostMethod.FIFO)
        assertNull(fifo.unitCost)
        assertEquals(3, fifo.quantity)
        assertEquals(0.0, fifo.value, 0.0)
    }

    @Test
    fun `the opening entry carries the purchase price already typed`() {
        val ledger = StockLedger(File(temp.root, StockLedger.FILE))
        val item = SavedItem(
            label = "Chaise",
            price = 250.0,
            purchasePrice = 175.0,
            stock = 4,
            id = "chair",
        )

        assertEquals(1, ledger.seed(listOf(item)))

        val entries = ledger.entriesChronological("chair")
        assertEquals(175.0, entries.single().unitCost, 0.001)
        assertEquals(700.0, StockCost.valuation(entries, CostMethod.CMP).value, 0.001)
    }

    @Test
    fun `a manual correction keeps the cost the ledger already knows`() {
        val ledger = StockLedger(File(temp.root, StockLedger.FILE))
        ledger.append(StockEntry("chair", 5, StockReason.PURCHASE, unitCost = 200.0))
        ledger.append(
            StockEntry(
                "chair",
                -1,
                StockReason.ADJUSTMENT,
                note = "Correction manuelle",
                unitCost = ledger.lastCost("chair") ?: 0.0,
            ),
        )

        assertEquals(200.0, ledger.lastCost("chair") ?: 0.0, 0.001)
        assertEquals(200.0, StockCost.unitCost(ledger.entriesChronological("chair"), CostMethod.CMP) ?: 0.0, 0.001)
    }

    @Test
    fun `a cost is written to the JSON only when it is known`() {
        val priced = buy(1, 99.5)
        val free = StockEntry("chair", 1, StockReason.PURCHASE)

        assertTrue(priced.toLine().contains("unitCost"))
        assertFalse(free.toLine().contains("unitCost"))
        // And a line written before the field existed still parses.
        val old = StockEntry.parse("""{"itemId":"chair","delta":1,"reason":"purchase"}""")
        assertEquals(0.0, old?.unitCost ?: -1.0, 0.0)
    }
}
