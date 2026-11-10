package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Goods receipts: what they do to the ledger and the catalogue, and what a
 * refused or deleted receipt must not do.
 */
class PurchaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class Shelf(var items: List<SavedItem>) : ItemShelf {
        override fun catalogue(): List<SavedItem> = items
        override fun writeCatalogue(items: List<SavedItem>) {
            this.items = items
        }
    }

    private fun item(id: String, stock: Int = 0, cost: Double = 0.0) = SavedItem(
        label = "Article $id",
        price = 100.0,
        stock = stock,
        purchasePrice = cost,
        id = id,
    )

    private fun store(
        shelf: Shelf = Shelf(listOf(item("chair", stock = 2), item("table", stock = 0))),
    ): Pair<PurchaseStore, Shelf> {
        val ledger = StockLedger(File(temp.root, StockLedger.FILE))
        val store = PurchaseStore(File(temp.root, PurchaseStore.FILE), ledger, shelf)
        return store to shelf
    }

    private fun receipt(
        number: String = "BR-20261110-0001",
        vararg lines: PurchaseLine,
    ) = Purchase(
        id = number,
        number = number,
        date = "10/11/2026",
        supplierName = "Bois du Nord",
        supplierIce = "001234567890123",
        lines = lines.toList(),
        createdAt = 1_000L,
    )

    @Test
    fun `receiving a purchase appends one entry per line and moves the cache`() {
        val (store, shelf) = store()

        val outcome = store.add(
            receipt(
                lines = arrayOf(
                    PurchaseLine("chair", "Chaise", quantity = 10, unitCostHt = 150.0),
                    PurchaseLine("table", "Table", quantity = 2, unitCostHt = 900.0),
                ),
            ),
        )

        assertEquals(PurchaseOutcome.Saved, outcome)
        // The shelf moved through the ledger, not by a direct write.
        assertEquals(12, shelf.items.first { it.id == "chair" }.stock)
        assertEquals(2, shelf.items.first { it.id == "table" }.stock)
        // The latest delivery becomes the article's purchase price.
        assertEquals(150.0, shelf.items.first { it.id == "chair" }.purchasePrice, 0.001)
        assertEquals(900.0, shelf.items.first { it.id == "table" }.purchasePrice, 0.001)
        assertEquals(1, store.all().size)
    }

    @Test
    fun `the ledger carries the cost of what was received`() {
        val (store, _) = store()
        store.add(receipt(lines = arrayOf(PurchaseLine("chair", "Chaise", 3, 120.0))))

        val entry = store.all().single()
        assertTrue(store.all().isNotEmpty())
        assertEquals("BR-20261110-0001", entry.number)
        // Read back from the ledger of the same store: the cost is written, not
        // only kept on the receipt.
        val ledger = StockLedger(File(temp.root, StockLedger.FILE))
        val movement = ledger.entriesChronological("chair").last()
        assertEquals(3, movement.delta)
        assertEquals(120.0, movement.unitCost, 0.001)
        assertEquals(StockReason.PURCHASE, movement.reason)
    }

    @Test
    fun `deleting a receipt reverses its entries and returns the shelf`() {
        val (store, shelf) = store()
        val purchase = receipt(
            lines = arrayOf(PurchaseLine("chair", "Chaise", 10, 150.0)),
        )
        store.add(purchase)
        assertEquals(12, shelf.items.first { it.id == "chair" }.stock)

        assertTrue(store.delete(purchase))

        assertEquals(2, shelf.items.first { it.id == "chair" }.stock)
        assertTrue(store.all().isEmpty())
        val ledger = StockLedger(File(temp.root, StockLedger.FILE))
        val entries = ledger.entriesChronological("chair")
        // A reversal, not a rewrite: both movements stay in the history.
        assertEquals(listOf(10, -10), entries.map { it.delta })
        assertEquals(0, ledger.balance("chair"))
    }

    @Test
    fun `the series numbers a day and continues after the highest`() {
        val date = GregorianCalendar(2026, Calendar.NOVEMBER, 10, 9, 0).time
        assertEquals("BR-20261110-0001", PurchaseNumber.peek(emptyList(), date))

        val existing = listOf(
            receipt("BR-20261110-0001"),
            receipt("BR-20261110-0003"),
            // Another day must not push today's sequence.
            receipt("BR-20251109-0042"),
        )
        assertEquals(4, PurchaseNumber.nextValue(existing, date))
        assertEquals("BR-20261110-0004", PurchaseNumber.peek(existing, date))
    }

    @Test
    fun `a receipt survives a JSON round trip`() {
        val (store, _) = store()
        val purchase = receipt(
            lines = arrayOf(
                PurchaseLine("chair", "Chaise", 4, 125.5),
                PurchaseLine("table", "Table", 1, 890.0),
            ),
        )
        store.add(purchase)

        // A second store reading the same file: what the next launch sees.
        val reopened = PurchaseStore(
            File(temp.root, PurchaseStore.FILE),
            StockLedger(File(temp.root, StockLedger.FILE)),
            Shelf(emptyList()),
        )
        val read = reopened.all().single()
        assertEquals(purchase.number, read.number)
        assertEquals("Bois du Nord", read.supplierName)
        assertEquals(2, read.lines.size)
        assertEquals(125.5, read.lines.first().unitCostHt, 0.001)
        assertEquals(1392.0, read.totalHt, 0.001)
    }

    @Test
    fun `an unknown article is refused before anything is written`() {
        val (store, shelf) = store()
        val outcome = store.add(
            receipt(
                lines = arrayOf(
                    PurchaseLine("chair", "Chaise", 5, 100.0),
                    PurchaseLine("ghost", "Article disparu", 1, 50.0),
                ),
            ),
        )

        assertTrue(outcome is PurchaseOutcome.UnknownItem)
        assertEquals("ghost", (outcome as PurchaseOutcome.UnknownItem).itemId)
        assertTrue(store.all().isEmpty())
        // Not even the valid line was applied: a half receipt is worse than none.
        assertEquals(2, shelf.items.first { it.id == "chair" }.stock)
        assertEquals(0, StockLedger(File(temp.root, StockLedger.FILE)).balance("chair"))
    }

    @Test
    fun `an empty receipt is refused`() {
        val (store, _) = store()
        assertTrue(store.add(receipt()) is PurchaseOutcome.Invalid)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `a deleted receipt frees its number for the same day`() {
        val (store, _) = store()
        val purchase = receipt(lines = arrayOf(PurchaseLine("chair", "Chaise", 1, 10.0)))
        store.add(purchase)
        assertNotNull(store.all().single())
        store.delete(purchase)

        val date = GregorianCalendar(2026, Calendar.NOVEMBER, 10, 15, 0).time
        assertEquals("BR-20261110-0001", store.nextNumber(date))
        assertFalse(store.all().isNotEmpty())
    }
}
