package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceJsonTest {

    private val full = InvoiceData(
        number = "D-20260101-0042",
        date = "01/01/2026",
        client = InvoiceClient(
            name = "Société Exemple",
            address = "12 rue des Fleurs, Casablanca",
            ice = "001234567000089",
            ifNumber = "12345678",
            rc = "98765",
            patente = "654321",
            cnss = "1234567",
            phone = "0600000000",
            email = "contact@exemple.ma",
            vatExempt = true,
        ),
        lines = listOf(
            InvoiceLine("Prestation", 2, 250.0, unit = "heure", lineDiscountPercent = 10, taxExempt = true),
            InvoiceLine("Fourniture", 1, 99.99),
        ),
        discountPercent = 7,
        vatPercent = 20.0,
        paymentMethod = PaymentMethod.CHECK,
        documentType = DocumentType.DEVIS,
        validityDate = "31/12/2026",
        supplier = InvoiceClient(name = "Fournisseur SARL", ice = "009876543000012"),
        referenceNumber = "F-20260101-0042",
        incoterm = "FOB Casablanca",
        vatNotApplicable = false,
    )

    @Test
    fun `a document survives a round trip unchanged`() {
        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(full))

        assertEquals(full, restored)
    }

    @Test
    fun `a document without a supplier or a validity date restores as nulls`() {
        val simple = full.copy(documentType = DocumentType.FACTURE, supplier = null, validityDate = null)

        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(simple))

        assertNull(restored.supplier)
        assertNull(restored.validityDate)
    }

    @Test
    fun `missing fields fall back instead of throwing`() {
        val minimal = org.json.JSONObject().put("number", "F-1").put("date", "01/01/2026")

        val restored = InvoiceJson.dataFromJson(minimal)

        assertEquals("F-1", restored.number)
        assertEquals(emptyList<InvoiceLine>(), restored.lines)
        assertEquals(DocumentType.FACTURE, restored.documentType)
        assertEquals(PaymentMethod.CASH, restored.paymentMethod)
        assertEquals("", restored.referenceNumber)
        assertEquals("", restored.incoterm)
        assertEquals(false, restored.vatNotApplicable)
    }

    @Test
    fun `a credit note keeps its reference and its own opening in words`() {
        val creditNote = InvoiceData(
            number = "A-20260101-0001",
            date = "01/01/2026",
            client = InvoiceClient(name = "Client"),
            lines = listOf(InvoiceLine("Produit", quantity = 1, unitPrice = 100.0)),
            documentType = DocumentType.AVOIR,
            referenceNumber = "F-20260101-0042",
        )

        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(creditNote))

        assertEquals(DocumentType.AVOIR, restored.documentType)
        assertEquals("F-20260101-0042", restored.referenceNumber)
        assertTrue(restored.amountInWords.contains("LE PRÉSENT AVOIR"))
    }
}
