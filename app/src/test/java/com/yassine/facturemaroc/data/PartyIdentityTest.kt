package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stable party identity: what makes a rename stop detaching history.
 *
 * The dangerous half is the migration, not the new field: it rewrites documents
 * that are already issued, so it must be idempotent, must never attach an
 * ambiguous name to the wrong account, and must leave anything it cannot resolve
 * working exactly as before.
 */
class PartyIdentityTest {

    private fun client(name: String, role: PartyRole = PartyRole.CLIENT, id: String = "") =
        InvoiceClient(name = name, role = role, id = id)

    private fun record(
        number: String,
        party: InvoiceClient,
        supplier: InvoiceClient? = null,
        type: DocumentType = DocumentType.FACTURE,
    ) = InvoiceRecord(
        id = number,
        data = InvoiceData(
            number = number,
            date = "10/11/2026",
            client = party,
            lines = listOf(InvoiceLine("Chaise", 1.0, 1_000.0)),
            documentType = type,
            supplier = supplier,
        ),
        status = InvoiceStatus.ISSUED,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `every party gets an id and keeps the one it had`() {
        val kept = client("Karim", id = "c1")
        val identified = PartyIdentity.withIds(listOf(kept, client("Atelier")))

        assertEquals("c1", identified[0].id)
        assertTrue(identified[1].id.isNotBlank())
        // Two calls in a row must not renumber anybody.
        assertEquals(identified, PartyIdentity.withIds(identified))
    }

    @Test
    fun `two rows cannot share an id after a hand-edited restore`() {
        val a = client("A", id = "same")
        val b = client("B", id = "same")

        val identified = PartyIdentity.withIds(listOf(a, b))

        assertEquals("same", identified[0].id)
        assertNotEquals("same", identified[1].id)
        assertTrue(identified[1].id.isNotBlank())
    }

    @Test
    fun `an issued document is stamped with the id of the party it names`() {
        val karim = client("Karim", id = "c1")
        val old = record("F-1", client("  karim "))

        val stamped = PartyIdentity.stamp(listOf(old), listOf(karim))

        assertEquals("c1", stamped.single().data.client.id)
    }

    @Test
    fun `an ambiguous name is left alone rather than attached to the wrong account`() {
        val first = client("Garage", id = "c1")
        val second = client("Garage", id = "c2")
        val old = record("F-1", client("Garage"))

        val stamped = PartyIdentity.stamp(listOf(old), listOf(first, second))

        assertEquals("", stamped.single().data.client.id)
    }

    @Test
    fun `stamping twice changes nothing the second time`() {
        val karim = client("Karim", id = "c1")
        val once = PartyIdentity.stamp(listOf(record("F-1", client("Karim"))), listOf(karim))

        val twice = PartyIdentity.stamp(once, listOf(karim))

        // The same list instance back, so the caller can skip a write.
        assertTrue(once === twice)
    }

    @Test
    fun `a document written for a walk-in client is never stamped`() {
        val old = record("F-1", client("Client comptoir"))

        val records = listOf(old)
        val stamped = PartyIdentity.stamp(records, listOf(client("Karim", id = "c1")))

        assertTrue("une liste inchangée doit être rendue telle quelle", records === stamped)
    }

    @Test
    fun `a goods receipt is linked to its supplier by name`() {
        val supplier = client("Bois du Nord", PartyRole.SUPPLIER, id = "s1")
        val receipt = Purchase(
            id = "p1",
            number = "BR-1",
            date = "02/11/2026",
            supplierName = " bois du nord ",
            lines = listOf(PurchaseLine("c1", "Planche", 10, 50.0)),
        )

        val linked = PartyIdentity.stampPurchases(listOf(receipt), listOf(supplier))

        assertEquals("s1", linked.single().supplierId)
        // The name on the paper does not move: it is what the receipt says.
        assertEquals(" bois du nord ", linked.single().supplierName)
    }

    @Test
    fun `the id decides over the name once both exist`() {
        val karim = client("Karim El Fassi", id = "c1")
        val issued = client("Karim", id = "c1")

        assertTrue(PartyIdentity.matches(issued, karim))
        // Same name, different party: an id must not be ignored in favour of a
        // string that happens to look the same.
        assertFalse(PartyIdentity.matches(issued, karim.copy(id = "c2")))
    }

    @Test
    fun `without ids the name still decides`() {
        assertTrue(
            PartyIdentity.matches(client(" Karim "), client("karim")),
        )
        // A client and a supplier can share a name; they are different accounts.
        assertFalse(
            PartyIdentity.matches(client("Karim"), client("Karim", PartyRole.SUPPLIER)),
        )
    }
}
