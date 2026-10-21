package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceDataTest {

    private fun invoice(
        vararg lines: InvoiceLine,
        discount: Int = 0,
        vat: Double = 20.0,
        client: InvoiceClient = InvoiceClient(name = "Client"),
        type: DocumentType = DocumentType.FACTURE,
    ) = InvoiceData(
        number = "F-20260101-0001",
        date = "01/01/2026",
        client = client,
        lines = lines.toList(),
        discountPercent = discount,
        vatPercent = vat,
        documentType = type,
    )

    @Test
    fun `an exempt line is left out of the tax base`() {
        val data = invoice(
            InvoiceLine("Conseil", quantity = 1, unitPrice = 100.0, taxExempt = true),
            InvoiceLine("Produit", quantity = 1, unitPrice = 200.0),
        )

        assertEquals(200.0, data.taxableSubtotal, 0.001)
        assertEquals(40.0, data.vatAmount, 0.001)
        assertEquals(340.0, data.total, 0.001)
    }

    @Test
    fun `the invoice discount is shared so an exempt line cannot reduce the tax`() {
        val data = invoice(
            InvoiceLine("Conseil", quantity = 1, unitPrice = 100.0, taxExempt = true),
            InvoiceLine("Produit", quantity = 1, unitPrice = 200.0),
            discount = 10,
        )

        // 300 - 10 % = 270. Of the 30 discount, 10 belongs to the exempt share
        // (100/300), so the taxed base is 200 - 20 = 180 and TVA is 36.
        assertEquals(30.0, data.discountAmount, 0.001)
        assertEquals(270.0, data.totalExcludingVat, 0.001)
        assertEquals(180.0, data.vatBase, 0.001)
        assertEquals(36.0, data.vatAmount, 0.001)
        assertEquals(306.0, data.total, 0.001)
    }

    @Test
    fun `without an exempt line the arithmetic is unchanged`() {
        val data = invoice(
            InvoiceLine("A", quantity = 1, unitPrice = 123.45),
            InvoiceLine("B", quantity = 1, unitPrice = 76.55),
            discount = 10,
        )

        assertEquals(200.0, data.subtotal, 0.001)
        assertEquals(180.0, data.vatBase, 0.001)
        assertEquals(36.0, data.vatAmount, 0.001)
        assertEquals(216.0, data.total, 0.001)
    }

    @Test
    fun `an exempt client pays no tax on any line`() {
        val data = invoice(
            InvoiceLine("Produit", quantity = 1, unitPrice = 500.0),
            client = InvoiceClient(name = "Export", vatExempt = true),
        )

        assertEquals(0.0, data.effectiveVatPercent, 0.001)
        assertEquals(0.0, data.vatAmount, 0.001)
        assertEquals(500.0, data.total, 0.001)
    }

    @Test
    fun `a line discount is applied before the invoice discount`() {
        val line = InvoiceLine("Produit", quantity = 2, unitPrice = 100.0, lineDiscountPercent = 25)

        assertEquals(150.0, line.total, 0.001)
    }

    @Test
    fun `an exempt line says so on the printed label`() {
        val line = InvoiceLine("Conseil", quantity = 1, unitPrice = 100.0, taxExempt = true)

        assertEquals("Conseil · TVA exonérée", line.lineLabel)
    }

    @Test
    fun `a quote spells out its own document name`() {
        val data = invoice(
            InvoiceLine("Prestation", quantity = 1, unitPrice = 100.0),
            type = DocumentType.DEVIS,
        )

        assertEquals(true, data.amountInWords.contains("LE PRÉSENT DEVIS"))
    }
}
