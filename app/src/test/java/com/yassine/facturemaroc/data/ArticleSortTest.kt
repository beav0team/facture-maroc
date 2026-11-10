package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleSortTest {

    private fun item(
        label: String,
        price: Double = 100.0,
        stock: Int? = null,
        purchase: Double = 0.0,
    ) = SavedItem(label = label, price = price, stock = stock, purchasePrice = purchase)

    private val chairs = item("Chaise", price = 350.0, stock = 4, purchase = 200.0)
    private val table = item("Table", price = 1200.0, stock = 0, purchase = 900.0)
    private val mounting = item("Montage", price = 150.0)
    private val cheapChair = item("chaise pliante", price = 90.0, stock = 20, purchase = 40.0)

    private val all = listOf(chairs, table, mounting, cheapChair)

    @Test
    fun `name order ignores case`() {
        assertEquals(
            listOf("Chaise", "chaise pliante", "Montage", "Table"),
            sortArticles(all, ArticleSort.NAME).map { it.label },
        )
    }

    @Test
    fun `price order is most expensive first`() {
        assertEquals(
            listOf(1200.0, 350.0, 150.0, 90.0),
            sortArticles(all, ArticleSort.PRICE).map { it.price },
        )
    }

    @Test
    fun `stock order puts what runs out first and services last`() {
        assertEquals(
            listOf("Table", "Chaise", "chaise pliante", "Montage"),
            sortArticles(all, ArticleSort.STOCK).map { it.label },
        )
    }

    @Test
    fun `margin order puts the best sale first and unknown costs last`() {
        // Chaise 150, chaise pliante 50, Table 300, Montage unknown.
        assertEquals(
            listOf("Table", "Chaise", "chaise pliante", "Montage"),
            sortArticles(all, ArticleSort.MARGIN).map { it.label },
        )
    }

    @Test
    fun `articles at the same price keep a stable name order`() {
        val twinA = item("Bureau", price = 500.0)
        val twinB = item("Armoire", price = 500.0)
        assertEquals(
            listOf("Armoire", "Bureau"),
            sortArticles(listOf(twinA, twinB), ArticleSort.PRICE).map { it.label },
        )
    }
}
