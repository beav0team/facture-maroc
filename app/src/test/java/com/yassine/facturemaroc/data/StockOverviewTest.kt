package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stock tab's ordering and totals.
 *
 * The rule that matters: the quantity shown is the journal's, the value is that
 * quantity at the shop's unit cost, and a service is never on a shelf.
 */
class StockOverviewTest {

    private fun article(
        id: String,
        label: String,
        stock: Int?,
        threshold: Int? = 4,
        purchase: Double = 100.0,
    ) = SavedItem(
        label = label,
        price = 200.0,
        stock = stock,
        reorderThreshold = threshold,
        purchasePrice = purchase,
        id = id,
    )

    @Test
    fun `services are not on a shelf`() {
        val items = listOf(
            article("a", "Chaise", 5),
            SavedItem(label = "Montage", price = 150.0, id = "s"),
        )
        val overview = stockOverview(items, mapOf("a" to 5))
        assertEquals(1, overview.lines.size)
        assertEquals("Chaise", overview.lines.single().item.label)
    }

    @Test
    fun `what needs reordering comes first, emptiest first`() {
        val items = listOf(
            article("a", "Chaise", 9),
            article("b", "Table", 0),
            article("c", "Armoire", 2),
            article("d", "Banc", 9),
        )
        val overview = stockOverview(
            items,
            mapOf("a" to 9, "b" to 0, "c" to 2, "d" to 9),
        )
        assertEquals(
            listOf("Table", "Armoire", "Banc", "Chaise"),
            overview.lines.map { it.item.label },
        )
        assertEquals(2, overview.toReorder)
    }

    @Test
    fun `the quantity is the journal's, not the cached field`() {
        val items = listOf(article("a", "Chaise", stock = 5))
        val overview = stockOverview(items, mapOf("a" to 3))
        assertEquals(3, overview.lines.single().onShelf)
        // And the disagreement is counted, not hidden.
        assertEquals(1, overview.drift)
    }

    @Test
    fun `a negative quantity is shown, not clamped`() {
        // A shop can sell a tyre that arrived this morning and was never
        // received in the app; hiding that would push it to zero and lose the
        // debt of one unit.
        val overview = stockOverview(listOf(article("a", "Pneu", 0)), mapOf("a" to -1))
        assertEquals(-1, overview.lines.single().onShelf)
        assertEquals(-1, overview.units)
    }

    @Test
    fun `the value uses the unit cost, and falls back to the purchase price`() {
        val items = listOf(
            article("a", "Chaise", 3, purchase = 100.0),
            article("b", "Table", 2, purchase = 500.0),
        )
        val overview = stockOverview(items, mapOf("a" to 3, "b" to 2), mapOf("a" to 120.0))
        assertEquals(3 * 120.0 + 2 * 500.0, overview.value, 0.001)
        assertEquals(5, overview.units)
    }

    @Test
    fun `an article with no known cost is worth nothing rather than a guess`() {
        val overview = stockOverview(
            listOf(article("a", "Chaise", 5, purchase = 0.0)),
            mapOf("a" to 5),
        )
        assertEquals(0.0, overview.value, 0.001)
        assertFalse(overview.lines.single().reorder)
    }

    @Test
    fun `an empty catalogue is an empty shelf`() {
        val overview = stockOverview(emptyList(), emptyMap())
        assertTrue(overview.lines.isEmpty())
        assertEquals(0, overview.units)
        assertEquals(0.0, overview.value, 0.001)
        assertEquals(0, overview.drift)
    }
}
