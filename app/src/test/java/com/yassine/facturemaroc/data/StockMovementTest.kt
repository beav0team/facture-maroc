package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What issuing a document does to the shelf.
 *
 * The rules under test are the ones a shopkeeper checks by walking to the shelf:
 * a facture takes stock out, a devis does not, an avoir puts it back, and a
 * service is never counted.
 */
class StockMovementTest {

    private fun invoice(
        vararg lines: InvoiceLine,
        type: DocumentType = DocumentType.FACTURE,
    ) = InvoiceData(
        number = "F-1",
        date = "29/10/2026",
        client = InvoiceClient("Client de passage"),
        lines = lines.toList(),
        documentType = type,
    )

    private fun line(label: String, quantity: Int) =
        InvoiceLine(label, quantity = quantity, unitPrice = 100.0)

    private fun product(label: String, stock: Int?, price: Double = 100.0) =
        SavedItem(label = label, price = price, stock = stock)

    @Test
    fun `a facture takes the sold quantity off the shelf`() {
        val items = listOf(product("Chaise en rotin", stock = 10))

        val after = StockMovement.apply(items, invoice(line("Chaise en rotin", 3)))

        assertEquals(7, after.single().stock)
    }

    @Test
    fun `a devis, a proforma and a bon de commande move nothing`() {
        val items = listOf(product("Chaise", stock = 4))

        listOf(DocumentType.DEVIS, DocumentType.PROFORMA, DocumentType.BON_DE_COMMANDE)
            .forEach { type ->
                val after = StockMovement.apply(items, invoice(line("Chaise", 2), type = type))
                assertEquals("$type must not move stock", 4, after.single().stock)
            }
    }

    @Test
    fun `an avoir puts the goods back`() {
        val items = listOf(product("Chaise", stock = 3))

        val after = StockMovement.apply(
            items,
            invoice(line("Chaise", 2), type = DocumentType.AVOIR),
        )

        assertEquals(5, after.single().stock)
    }

    @Test
    fun `a ticket is a sale like any other`() {
        val items = listOf(product("Coussin", stock = 20))

        val after = StockMovement.apply(
            items,
            invoice(line("Coussin", 4), type = DocumentType.TICKET),
        )

        assertEquals(16, after.single().stock)
    }

    @Test
    fun `two lines of the same product cost two units, not one`() {
        val items = listOf(product("Chaise", stock = 10))

        val after = StockMovement.apply(items, invoice(line("Chaise", 2), line("Chaise", 1)))

        assertEquals(7, after.single().stock)
    }

    @Test
    fun `a service has no quantity to count`() {
        val items = listOf(
            product("Montage", stock = null, price = 50.0),
            product("Chaise", stock = 8),
        )

        val after = StockMovement.apply(items, invoice(line("Montage", 1), line("Chaise", 2)))

        assertNull(after.first { it.label == "Montage" }.stock)
        assertEquals(6, after.first { it.label == "Chaise" }.stock)
    }

    @Test
    fun `a line that matches no item leaves the catalogue untouched`() {
        val items = listOf(product("Chaise", stock = 8))

        val after = StockMovement.apply(items, invoice(line("Pose de film", 1)))

        assertEquals(items, after)
    }

    @Test
    fun `labels match whatever the case and the spacing`() {
        val items = listOf(product("Chaise en rotin", stock = 5))

        val after = StockMovement.apply(items, invoice(line("  chaise en rotin ", 1)))

        assertEquals(4, after.single().stock)
    }

    @Test
    fun `selling more than was recorded shows a negative, not a zero`() {
        val items = listOf(product("Chaise", stock = 2))

        val after = StockMovement.apply(items, invoice(line("Chaise", 5)))

        assertEquals(-3, after.single().stock)
        assertTrue(after.single().outOfStock)
    }

    @Test
    fun `reversing an issue gives the shelf back exactly as it was`() {
        val items = listOf(product("Chaise", stock = 10))
        val data = invoice(line("Chaise", 4))

        val issued = StockMovement.apply(items, data)
        val restored = StockMovement.apply(issued, data, reversing = true)

        assertEquals(6, issued.single().stock)
        assertEquals(10, restored.single().stock)
    }

    @Test
    fun `reversing a devis still moves nothing`() {
        val items = listOf(product("Chaise", stock = 10))

        val after = StockMovement.apply(
            items,
            invoice(line("Chaise", 4), type = DocumentType.DEVIS),
            reversing = true,
        )

        assertEquals(10, after.single().stock)
    }

    @Test
    fun `changed reports only the rows that moved`() {
        val items = listOf(
            product("Chaise", stock = 10),
            product("Coussin", stock = 5),
        )

        val moved = StockMovement.changed(items, invoice(line("Chaise", 1)))

        assertEquals(listOf("Chaise"), moved.map { it.label })
    }
}
