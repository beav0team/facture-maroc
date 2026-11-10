package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-shot identity migration.
 *
 * The properties worth pinning are the ones that would quietly corrupt a shelf
 * if they were wrong: an id must not be re-rolled, a line must not link to the
 * wrong article when two share a name, and a line that matches nothing must stay
 * exactly as it was so an issued document keeps printing what the customer read.
 */
class ItemMigrationTest {

    private fun item(label: String, id: String = "", stock: Int? = null) =
        SavedItem(id = id, label = label, price = 100.0, stock = stock)

    private fun record(vararg lines: InvoiceLine) = InvoiceRecord(
        id = "record-1",
        data = InvoiceData(
            number = "F-1",
            date = "10/11/2026",
            client = InvoiceClient("Client"),
            lines = lines.toList(),
        ),
        status = InvoiceStatus.ISSUED,
        createdAt = 1L,
        updatedAt = 2L,
    )

    private fun line(label: String, itemId: String? = null) =
        InvoiceLine(label, quantity = 1.0, unitPrice = 100.0, itemId = itemId)

    @Test
    fun `every article without an id is given one`() {
        var next = 0
        val plan = ItemMigration.plan(
            listOf(item("Chaise"), item("Coussin")),
            emptyList(),
        ) { "id-${next++}" }

        assertEquals(2, plan.items.map { it.id }.distinct().size)
        assertTrue(plan.items.all { it.id.isNotBlank() })
        assertTrue(plan.changed)
    }

    @Test
    fun `a second launch changes nothing`() {
        val first = ItemMigration.plan(
            listOf(item("Chaise"), item("Coussin")),
            listOf(record(line("chaise "))),
        )

        val second = ItemMigration.plan(first.items, first.records)

        assertFalse(second.changed)
        assertEquals(first.items, second.items)
        assertEquals(first.records, second.records)
    }

    @Test
    fun `an existing id is never re-rolled`() {
        val plan = ItemMigration.plan(listOf(item("Chaise", id = "stable")), emptyList())

        assertEquals("stable", plan.items.single().id)
        assertFalse(plan.changed)
    }

    @Test
    fun `lines link by label ignoring case and surrounding space`() {
        val plan = ItemMigration.plan(
            listOf(item("Chaise en rotin", id = "chair")),
            listOf(record(line("  chaise EN rotin "))),
        )

        assertEquals("chair", plan.records.single().data.lines.single().itemId)
    }

    @Test
    fun `two articles sharing a label link nothing`() {
        // The store refuses duplicates, but a hand-edited file or a bad merge
        // can still hold them. Picking one would move the wrong shelf, so the
        // line is left unlinked rather than guessed.
        val plan = ItemMigration.plan(
            listOf(item("Chaise", id = "a"), item("Chaise", id = "b")),
            listOf(record(line("Chaise"))),
        )

        assertNull(plan.records.single().data.lines.single().itemId)
        assertEquals(setOf("a", "b"), plan.items.map { it.id }.toSet())
    }

    @Test
    fun `a line matching no article stays unlinked and still reads`() {
        val plan = ItemMigration.plan(
            listOf(item("Chaise", id = "chair")),
            listOf(record(line("Pose de film"))),
        )

        assertNull(plan.records.single().data.lines.single().itemId)
        assertEquals("Pose de film", plan.records.single().data.lines.single().description)
    }

    @Test
    fun `a duplicate id on two articles is replaced, not dropped`() {
        val plan = ItemMigration.plan(
            listOf(item("Chaise", id = "same"), item("Coussin", id = "same")),
            emptyList(),
        ) { "fresh" }

        assertEquals(setOf("same", "fresh"), plan.items.map { it.id }.toSet())
    }

    @Test
    fun `a line that already has an id is left alone`() {
        val plan = ItemMigration.plan(
            listOf(item("Chaise", id = "chair")),
            listOf(record(line("Ancien nom", itemId = "chair"))),
        )

        assertEquals("chair", plan.records.single().data.lines.single().itemId)
        assertFalse(plan.changed)
    }
}
