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
        assertEquals(1, line.quantity)
    }

    @Test
    fun `an item without a unit adds a clean line`() {
        val line = SavedItem(label = "Frais de dossier", price = 50.0).toLine()

        assertEquals("Frais de dossier", line.description)
        assertEquals("Frais de dossier", line.lineLabel)
    }
}
