package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An avoir must reverse its invoice to the centime.
 *
 * Everything that describes the sale is copied unchanged — if the credit
 * recomputed a price, the invoice and its avoir would not cancel and the
 * customer's account would drift by the difference. Only what belongs to the
 * money that moved is cleared.
 */
class CreditNoteTest {

    private fun invoice(
        type: DocumentType = DocumentType.FACTURE,
        payment: PaymentInfo? = PaymentInfo(paidAmount = 240.0, paidAt = "12/11/2026"),
    ) = InvoiceRecord(
        id = "r1",
        data = InvoiceData(
            number = "F-20261110-0001",
            date = "10/11/2026",
            client = InvoiceClient("Société BTP", ice = "009876543000012"),
            lines = listOf(
                InvoiceLine("Chaise", 2.0, 950.0, vatPercent = 20.0),
                InvoiceLine("Montage", 1.0, 300.0, vatPercent = 10.0),
            ),
            discountFixed = 50.0,
            dueDate = "10/12/2026",
            validityDate = "30/11/2026",
            documentType = type,
            payments = listOfNotNull(payment),
        ),
        status = InvoiceStatus.ISSUED,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `an invoice becomes an avoir that names it`() {
        val source = invoice()
        val avoir = CreditNote.from(source)!!

        assertEquals(DocumentType.AVOIR, avoir.data.documentType)
        assertEquals("F-20261110-0001", avoir.data.referenceNumber)
    }

    @Test
    fun `the avoir carries the same lines and the same total`() {
        val source = invoice()
        val avoir = CreditNote.from(source)!!

        assertEquals(source.data.lines, avoir.data.lines)
        assertEquals(source.data.total, avoir.data.total, 0.001)
        assertEquals(source.data.discountFixed, avoir.data.discountFixed, 0.001)
    }

    @Test
    fun `money that moved stays on the invoice`() {
        val avoir = CreditNote.from(invoice())!!

        assertTrue(avoir.data.payments.isEmpty())
        assertNull(avoir.data.dueDate)
        assertNull(avoir.data.validityDate)
    }

    @Test
    fun `an avoir starts unpaid even when the invoice was settled`() {
        val avoir = CreditNote.from(invoice())!!

        assertEquals(PaymentState.UNPAID, avoir.data.paymentState)
    }

    @Test
    fun `only documents that sold something can be credited`() {
        assertTrue(DocumentType.FACTURE.canBeCredited)
        assertTrue(DocumentType.TICKET.canBeCredited)
        assertTrue(DocumentType.SIMPLIFIEE.canBeCredited)
        assertTrue(DocumentType.EXPORT.canBeCredited)

        assertFalse(DocumentType.AVOIR.canBeCredited)
        assertFalse(DocumentType.DEVIS.canBeCredited)
        assertFalse(DocumentType.PROFORMA.canBeCredited)
        assertFalse(DocumentType.BON_DE_COMMANDE.canBeCredited)
        assertFalse(DocumentType.ACOMPTE.canBeCredited)
        assertFalse(DocumentType.REGULARISATION.canBeCredited)
    }

    @Test
    fun `crediting a credit note is refused`() {
        assertNull(CreditNote.from(invoice(type = DocumentType.AVOIR)))
    }
}
