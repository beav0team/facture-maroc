package com.yassine.facturemaroc.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A document that has left the phone cannot be deleted.
 *
 * This is the rule that keeps the history usable as accounts: the customer
 * holds the PDF, so removing the record would leave the shop with a gap it
 * cannot explain to a tax inspector. Corrections go through an avoir.
 */
class InvoiceDeletionTest {

    private fun record(status: InvoiceStatus) = InvoiceRecord(
        id = "r1",
        data = InvoiceData(
            number = "F-20261110-0001",
            date = "10/11/2026",
            client = InvoiceClient("Client"),
            lines = listOf(InvoiceLine("Prestation", 1, 100.0)),
            documentType = DocumentType.FACTURE,
        ),
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `a draft can be deleted`() {
        assertTrue(record(InvoiceStatus.DRAFT).isDeletable)
    }

    @Test
    fun `an issued document cannot`() {
        assertFalse(record(InvoiceStatus.ISSUED).isDeletable)
    }

    @Test
    fun `issuing a draft takes the delete away`() {
        val draft = record(InvoiceStatus.DRAFT)
        val issued = draft.copy(status = InvoiceStatus.ISSUED)

        assertTrue(draft.isDeletable)
        assertFalse(issued.isDeletable)
    }
}
