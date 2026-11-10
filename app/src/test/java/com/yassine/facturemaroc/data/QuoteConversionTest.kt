package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An invoice built from a quote must bill what the customer accepted.
 *
 * Every figure travels unchanged; only the document's own life differs (no
 * validity date, no deadline) and the quote number is kept as the reference.
 */
class QuoteConversionTest {

    private fun quote(type: DocumentType = DocumentType.DEVIS) = InvoiceRecord(
        id = "q1",
        data = InvoiceData(
            number = "D-20261101-0003",
            date = "01/11/2026",
            client = InvoiceClient("Société BTP"),
            lines = listOf(
                InvoiceLine("Chaise", 2.0, 950.0, vatPercent = 20.0),
                InvoiceLine("Montage", 3.0, 450.0, vatPercent = 10.0),
            ),
            discountPercent = 5,
            surchargeLabel = "Livraison",
            surchargeAmount = 80.0,
            validityDate = "30/11/2026",
            documentType = type,
        ),
        status = InvoiceStatus.ISSUED,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `a quote becomes an invoice that names it`() {
        val invoice = QuoteConversion.invoiceFrom(quote())!!

        assertEquals(DocumentType.FACTURE, invoice.data.documentType)
        assertEquals("D-20261101-0003", invoice.data.referenceNumber)
    }

    @Test
    fun `the invoice bills exactly what was quoted`() {
        val source = quote()
        val invoice = QuoteConversion.invoiceFrom(source)!!

        assertEquals(source.data.lines, invoice.data.lines)
        assertEquals(source.data.total, invoice.data.total, 0.001)
        assertEquals(source.data.vatRows.size, invoice.data.vatRows.size)
    }

    @Test
    fun `the quote's validity date does not follow the invoice`() {
        val invoice = QuoteConversion.invoiceFrom(quote())!!

        assertNull(invoice.data.validityDate)
        assertNull(invoice.data.dueDate)
    }

    @Test
    fun `only a quote converts`() {
        assertTrue(QuoteConversion.invoiceFrom(quote()) != null)
        assertNull(QuoteConversion.invoiceFrom(quote(DocumentType.FACTURE)))
        assertNull(QuoteConversion.invoiceFrom(quote(DocumentType.AVOIR)))
        assertNull(QuoteConversion.invoiceFrom(quote(DocumentType.PROFORMA)))
    }
}
