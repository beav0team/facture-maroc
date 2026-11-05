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

    private val tyre = SavedItem(
        label = "Pneu 205/55 R16",
        price = 950.0,
        unit = "unité",
        folder = "Pneus",
        barcode = "6111234500017",
        purchasePrice = 700.0,
        stock = 12,
    )
    private val winter = tyre.copy(label = "Pneu hiver 205/55", folder = "Pneus/Hiver", stock = 4)
    private val valve = SavedItem("Valve", 10.0, stock = 30)
    private val fitting = SavedItem("Montage", 50.0)

    private val catalogue = listOf(tyre, winter, valve, fitting)

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
            listOf("Montage", "Pneu 205/55 R16", "Pneu hiver 205/55", "Valve"),
            shown.map { it.label },
        )
    }

    @Test
    fun `filtering on a folder includes its sub-folders`() {
        val shown = filterArticles(catalogue, "Pneus")

        // A tyre filed under Pneus/Hiver is still in Pneus: hiding it would
        // read as stock that went missing.
        assertEquals(listOf("Pneu 205/55 R16", "Pneu hiver 205/55"), shown.map { it.label })
    }

    @Test
    fun `filtering on no folder is its own filter`() {
        val shown = filterArticles(catalogue, "")

        assertEquals(listOf("Montage", "Valve"), shown.map { it.label })
    }

    @Test
    fun `a folder nothing is filed in comes back empty, not whole`() {
        assertTrue(filterArticles(catalogue, "Vélo").isEmpty())
    }

    @Test
    fun `the summary counts units and the money on the shelves`() {
        val summary = summarizeArticles(catalogue, ItemField.entries.toSet())

        // tyre 12, winter 4, valve 30; the fitting has no quantity.
        assertEquals(4, summary.count)
        assertEquals(46, summary.units)
        // 12 x 700 + 4 x 700 + 30 x 0: the winter tyre inherits the purchase
        // price from `tyre` through copy(), and the valve has none.
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
}
