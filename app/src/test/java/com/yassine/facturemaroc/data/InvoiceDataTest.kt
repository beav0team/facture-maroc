package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `auto entrepreneur charges no tax whatever the configured rate`() {
        val data = InvoiceData(
            number = "F-20260101-0001",
            date = "01/01/2026",
            client = InvoiceClient(name = "Client"),
            lines = listOf(InvoiceLine("Produit", quantity = 1, unitPrice = 100.0)),
            vatPercent = 20.0,
            vatNotApplicable = true,
        )

        assertEquals(0.0, data.effectiveVatPercent, 0.001)
        assertEquals(0.0, data.vatAmount, 0.001)
        assertEquals(100.0, data.total, 0.001)
    }

    @Test
    fun `an exempt client still pays no tax even when auto entrepreneur is off`() {
        val data = InvoiceData(
            number = "F-20260101-0001",
            date = "01/01/2026",
            client = InvoiceClient(name = "Export", vatExempt = true),
            lines = listOf(InvoiceLine("Produit", quantity = 1, unitPrice = 100.0)),
            vatPercent = 20.0,
            vatNotApplicable = false,
        )

        assertEquals(0.0, data.effectiveVatPercent, 0.001)
        assertEquals(0.0, data.vatAmount, 0.001)
    }

    @Test
    fun `a credit note carries the invoice it replaces`() {
        val data = InvoiceData(
            number = "A-20260101-0001",
            date = "01/01/2026",
            client = InvoiceClient(name = "Client"),
            lines = listOf(InvoiceLine("Produit", quantity = 1, unitPrice = 100.0)),
            documentType = DocumentType.AVOIR,
            referenceNumber = "F-20260101-0042",
        )

        assertTrue(data.showReferenceField)
        assertEquals("F-20260101-0042", data.referenceNumber)
        assertTrue(data.amountInWords.contains("LE PRÉSENT AVOIR"))
    }

    @Test
    fun `an export carries its incoterm`() {
        val data = InvoiceData(
            number = "E-20260101-0001",
            date = "01/01/2026",
            client = InvoiceClient(name = "Client"),
            lines = listOf(InvoiceLine("Produit", quantity = 1, unitPrice = 100.0)),
            documentType = DocumentType.EXPORT,
            incoterm = "FOB Casablanca",
        )

        assertTrue(data.showIncoterm)
        assertEquals("FOB Casablanca", data.incoterm)
    }

    @Test
    fun `an ordinary invoice has neither reference nor incoterm`() {
        val data = invoice(InvoiceLine("Produit", quantity = 1, unitPrice = 100.0))

        assertFalse(data.showReferenceField)
        assertFalse(data.showIncoterm)
        assertEquals("", data.referenceNumber)
        assertEquals("", data.incoterm)
    }

    @Test
    fun `a bon de commande is addressed to the supplier, not the client`() {
        val data = InvoiceData(
            number = "BC-20260101-0001",
            date = "01/01/2026",
            client = InvoiceClient(name = "Le magasin"),
            supplier = InvoiceClient(name = "Grossiste Atlas", ice = "007711223000045"),
            lines = listOf(InvoiceLine("Pneu", quantity = 1, unitPrice = 100.0)),
            documentType = DocumentType.BON_DE_COMMANDE,
        )

        assertEquals("Grossiste Atlas", data.partyName)
        assertEquals("Fournisseur", data.partyLabel)
        assertEquals("007711223000045", data.party.ice)
    }

    @Test
    fun `an order form with no supplier names nobody`() {
        val data = InvoiceData(
            number = "BC-20260101-0001",
            date = "01/01/2026",
            client = InvoiceClient(name = "Le magasin"),
            lines = listOf(InvoiceLine("Pneu", quantity = 1, unitPrice = 100.0)),
            documentType = DocumentType.BON_DE_COMMANDE,
        )

        assertEquals("", data.partyName)
        assertEquals("Fournisseur", data.partyLabel)
    }

    @Test
    fun `a sale with no identified buyer is a walk-in, not a blank`() {
        val data = invoice(
            InvoiceLine("Pneu", quantity = 1, unitPrice = 100.0),
            client = InvoiceClient(name = ""),
        )

        assertEquals("Client de passage", data.partyName)
        assertEquals("Client", data.partyLabel)
    }

    @Test
    fun `the history titles an order form with its supplier`() {
        val record = InvoiceRecord(
            id = "r1",
            data = InvoiceData(
                number = "BC-20260101-0001",
                date = "01/01/2026",
                client = InvoiceClient(name = "Mon magasin"),
                supplier = InvoiceClient(name = "Grossiste Atlas"),
                lines = emptyList(),
                documentType = DocumentType.BON_DE_COMMANDE,
            ),
            status = InvoiceStatus.ISSUED,
            createdAt = 1L,
            updatedAt = 1L,
        )

        // Not "Mon magasin": on a bon de commande the shop is the client, and
        // a history that named the shop on every order form was unreadable.
        assertEquals("Grossiste Atlas", record.title)
    }

    @Test
    fun `a walk-in sale is still titled as one`() {
        val record = InvoiceRecord(
            id = "r2",
            data = invoice(
                InvoiceLine("Pneu", quantity = 1, unitPrice = 100.0),
                client = InvoiceClient(name = ""),
            ),
            status = InvoiceStatus.ISSUED,
            createdAt = 1L,
            updatedAt = 1L,
        )

        assertEquals("Client de passage", record.title)
    }

    @Test
    fun `an order form with no supplier falls back to its type`() {
        val record = InvoiceRecord(
            id = "r3",
            data = InvoiceData(
                number = "BC-20260101-0002",
                date = "01/01/2026",
                client = InvoiceClient(name = "Mon magasin"),
                lines = emptyList(),
                documentType = DocumentType.BON_DE_COMMANDE,
            ),
            status = InvoiceStatus.DRAFT,
            createdAt = 1L,
            updatedAt = 1L,
        )

        assertEquals("Bon de commande", record.title)
    }
}
