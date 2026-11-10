package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which article fields a shop asked to see, and what the folder filter shows.
 *
 * The switches are not cosmetic: they decide whether the editor asks for a
 * value, but never whether the value is kept. That distinction is tested here
 * rather than on the screen because it is the kind of rule a later edit
 * "simplifies" while the shop is not looking.
 */
class ItemFieldsTest {

    private val everything = ShopSettings()

    private val chair = SavedItem(
        label = "Chaise en rotin",
        price = 950.0,
        unit = "unité",
        folder = "Mobilier",
        barcode = "6111234500017",
        purchasePrice = 700.0,
        stock = 12,
    )
    private val cushion = chair.copy(label = "Coussin", folder = "Mobilier/Textile", stock = 4)
    private val table = SavedItem("Table basse", 10.0, stock = 30)
    private val fitting = SavedItem("Montage", 50.0)

    private val catalogue = listOf(chair, cushion, table, fitting)

    @Test
    fun `an installation that never touched the settings shows every field`() {
        assertEquals(ItemField.entries.toSet(), visibleItemFields(ShopSettings()))
    }

    @Test
    fun `each switch removes exactly its own field`() {
        val onlyLabelAndPrice = visibleItemFields(
            everything.copy(
                showItemBarcode = false,
                showItemPurchasePrice = false,
                showItemUnit = false,
                showItemStock = false,
                showItemFolder = false,
            ),
        )

        assertTrue(onlyLabelAndPrice.isEmpty())

        val noStock = visibleItemFields(everything.copy(showItemStock = false))
        assertFalse(ItemField.STOCK in noStock)
        assertTrue(ItemField.BARCODE in noStock)
        assertTrue(ItemField.PURCHASE_PRICE in noStock)
        assertTrue(ItemField.UNIT in noStock)
        assertTrue(ItemField.FOLDER in noStock)
    }

    @Test
    fun `no filter shows the whole catalogue, sorted by designation`() {
        val shown = filterArticles(catalogue, null)

        assertEquals(4, shown.size)
        assertEquals(
            listOf("Chaise en rotin", "Coussin", "Montage", "Table basse"),
            shown.map { it.label },
        )
    }

    @Test
    fun `filtering on a folder includes its sub-folders`() {
        val shown = filterArticles(catalogue, "Mobilier")

        // An article filed under Mobilier/Textile is still in Mobilier: hiding
        // it would read as stock that went missing.
        assertEquals(listOf("Chaise en rotin", "Coussin"), shown.map { it.label })
    }

    @Test
    fun `filtering on no folder is its own filter`() {
        val shown = filterArticles(catalogue, "")

        assertEquals(listOf("Montage", "Table basse"), shown.map { it.label })
    }

    @Test
    fun `a folder nothing is filed in comes back empty, not whole`() {
        assertTrue(filterArticles(catalogue, "Vélo").isEmpty())
    }

    @Test
    fun `the summary counts units and the money on the shelves`() {
        val summary = summarizeArticles(catalogue, ItemField.entries.toSet())

        // chair 12, cushion 4, table 30; the fitting has no quantity.
        assertEquals(4, summary.count)
        assertEquals(46, summary.units)
        // 12 x 700 + 4 x 700 + 30 x 0: the cushion inherits the purchase
        // price from `chair` through copy(), and the table has none.
        assertEquals(11_200.0, summary.stockValue, 0.001)
    }

    @Test
    fun `the summary does not count what the shop hid`() {
        val noStock = summarizeArticles(catalogue, ItemField.entries.toSet() - ItemField.STOCK)
        assertEquals(0, noStock.units)
        assertEquals(0.0, noStock.stockValue, 0.001)
        assertEquals(4, noStock.count)

        val noPurchase = summarizeArticles(
            catalogue,
            ItemField.entries.toSet() - ItemField.PURCHASE_PRICE,
        )
        assertEquals(46, noPurchase.units)
        assertEquals(0.0, noPurchase.stockValue, 0.001)
    }

    @Test
    fun `a threshold only matters for a counted article`() {
        val low = chair.copy(stock = 2, reorderThreshold = 2)
        val above = chair.copy(stock = 3, reorderThreshold = 2)
        val noThreshold = chair.copy(stock = 0, reorderThreshold = null)
        val service = fitting.copy(reorderThreshold = 10)

        assertTrue(low.needsReorder)
        assertFalse(above.needsReorder)
        assertFalse(noThreshold.needsReorder)
        assertFalse(service.needsReorder)
    }

    @Test
    fun `the reorder filter keeps only what needs ordering`() {
        val low = chair.copy(label = "Low", stock = 1, reorderThreshold = 2)
        val plenty = chair.copy(label = "Plenty", stock = 20, reorderThreshold = 2)
        val list = listOf(low, plenty, cushion, fitting)

        assertEquals(listOf("Low"), filterToReorder(list, only = true).map { it.label })
        assertEquals(list, filterToReorder(list, only = false))
    }

    @Test
    fun `the summary counts only the articles at their threshold`() {
        val list = listOf(
            chair.copy(stock = 2, reorderThreshold = 2),
            cushion.copy(stock = 20, reorderThreshold = 2),
            table.copy(stock = 0, reorderThreshold = null),
        )

        assertEquals(1, summarizeArticles(list, ItemField.entries.toSet()).reorderCount)
        // Hiding Stock hides the count: the shop does not count, so the app
        // must not claim a shelf is low.
        assertEquals(
            0,
            summarizeArticles(list, ItemField.entries.toSet() - ItemField.STOCK).reorderCount,
        )
    }
}
