package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedItemTest {

    @Test
    fun `the unit is printed once, by the line, not twice in its name`() {
        val line = SavedItem(label = "Main-d'œuvre", price = 150.0, unit = "heure").toLine()

        assertEquals("Main-d'œuvre", line.description)
        assertEquals("heure", line.unit)
        assertEquals("Main-d'œuvre · heure", line.lineLabel)
    }

    @Test
    fun `the price is copied into the line, not referenced`() {
        val item = SavedItem(label = "Déplacement", price = 100.0)
        val line = item.toLine()

        assertEquals(100.0, line.unitPrice, 0.001)
        assertEquals(1.0, line.quantity, 0.0)
    }

    @Test
    fun `an item without a unit adds a clean line`() {
        val line = SavedItem(label = "Frais de dossier", price = 50.0).toLine()

        assertEquals("Frais de dossier", line.description)
        assertEquals("Frais de dossier", line.lineLabel)
    }

    @Test
    fun `a line carries the id of the article it came from`() {
        val line = SavedItem(label = "Chaise", price = 100.0, id = "chair").toLine()

        assertEquals("chair", line.itemId)
    }

    @Test
    fun `a rename is found by id before its new label`() {
        val items = listOf(
            SavedItem(label = "Chaise", price = 100.0, id = "chair"),
            SavedItem(label = "Coussin", price = 50.0, id = "cushion"),
        )

        val renamed = SavedItem(label = "Chaise en rotin", price = 100.0, id = "chair")

        assertEquals(0, SavedItemStore.indexOfIdentity(items, renamed))
    }

    @Test
    fun `an article with no id still replaces the one with its label`() {
        val items = listOf(SavedItem(label = "Chaise", price = 100.0, id = "chair"))

        assertEquals(
            0,
            SavedItemStore.indexOfIdentity(items, SavedItem(label = "  chaise ", price = 120.0)),
        )
    }

    @Test
    fun `an unknown article is appended, not mistaken for another`() {
        val items = listOf(SavedItem(label = "Chaise", price = 100.0, id = "chair"))

        assertEquals(
            -1,
            SavedItemStore.indexOfIdentity(items, SavedItem(label = "Table", price = 10.0, id = "table")),
        )
    }
}
