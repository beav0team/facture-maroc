package com.yassine.facturemaroc.invoice

import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.ShopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceXmlTest {

    private val shop = ShopSettings(
        name = "Atelier A&B",
        address = "12 rue <des Fleurs>",
        ice = "001234567000089",
        ifNumber = "12345678",
        rc = "98765",
        patente = "654321",
        cnss = "1234567",
        vatPercent = 20.0,
    )

    private val data = InvoiceData(
        number = "F-20261021-0001",
        date = "21/10/2026",
        client = InvoiceClient(
            name = "Client \"Export\" & Fils",
            address = "Casablanca",
            ice = "009876543000012",
        ),
        lines = listOf(
            InvoiceLine("Conseil", quantity = 1, unitPrice = 100.0, taxExempt = true),
            InvoiceLine("Produit", quantity = 2, unitPrice = 100.0, unit = "unité"),
        ),
        discountPercent = 10,
        vatPercent = 20.0,
    )

    @Test
    fun `the file starts with a declaration and a stable namespace`() {
        val xml = InvoiceXml.build(data, shop)

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(xml.contains("xmlns=\"${InvoiceXml.NAMESPACE}\""))
        assertTrue(xml.contains("type=\"facture\""))
        assertTrue(xml.contains("devise=\"MAD\""))
        assertTrue(xml.trimEnd().endsWith("</Facture>"))
    }

    @Test
    fun `an ampersand or an angle bracket cannot break the document`() {
        val xml = InvoiceXml.build(data, shop)

        assertTrue(xml.contains("Client &quot;Export&quot; &amp; Fils"))
        assertTrue(xml.contains("Atelier A&amp;B"))
        assertTrue(xml.contains("12 rue &lt;des Fleurs&gt;"))
        assertFalse(xml.contains("Client \"Export\""))
    }

    @Test
    fun `dates are written in ISO form even though the paper is French`() {
        val xml = InvoiceXml.build(data, shop)

        assertTrue(xml.contains("<Date>2026-10-21</Date>"))
    }

    @Test
    fun `money uses a dot, so an accounting tool can read it`() {
        val xml = InvoiceXml.build(data, shop)

        // 100 exempt + 200 taxed, 10 % off: base 180, TVA 36, total 306.
        assertTrue(xml.contains("<BaseTVA>180.00</BaseTVA>"))
        assertTrue(xml.contains("<MontantTVA>36.00</MontantTVA>"))
        assertTrue(xml.contains("<TotalHT>270.00</TotalHT>"))
        assertTrue(xml.contains("<TotalTTC>306.00</TotalTTC>"))
        assertFalse(xml.contains("306,00"))
    }

    @Test
    fun `an exempt line is marked as such in the line itself`() {
        val xml = InvoiceXml.build(data, shop)

        assertTrue(xml.contains("<ExonereeTVA>true</ExonereeTVA>"))
        assertTrue(xml.contains("<ExonereeTVA>false</ExonereeTVA>"))
    }

    @Test
    fun `the amount in words travels with the file`() {
        val xml = InvoiceXml.build(data, shop)

        assertTrue(xml.contains("TROIS CENT SIX DIRHAMS"))
        assertTrue(xml.contains("ARRÊTÉE LA PRÉSENTE FACTURE"))
    }

    @Test
    fun `a quote carries its validity date`() {
        val quote = data.copy(documentType = DocumentType.DEVIS, validityDate = "31/12/2026")

        val xml = InvoiceXml.build(quote, shop)

        assertTrue(xml.contains("<ValableJusquau>2026-12-31</ValableJusquau>"))
        assertTrue(xml.contains("type=\"devis\""))
    }

    @Test
    fun `the file name follows the document type`() {
        assertEquals("facture_F-20261021-0001.xml", InvoiceXml.fileName(data))
        assertEquals(
            "devis_D-20261021-0001.xml",
            InvoiceXml.fileName(data.copy(documentType = DocumentType.DEVIS, number = "D-20261021-0001")),
        )
        assertEquals(
            "bon_commande_BC-20261021-0001.xml",
            InvoiceXml.fileName(
                data.copy(documentType = DocumentType.BON_DE_COMMANDE, number = "BC-20261021-0001"),
            ),
        )
        assertEquals(
            "avoir_A-20261021-0001.xml",
            InvoiceXml.fileName(data.copy(documentType = DocumentType.AVOIR, number = "A-20261021-0001")),
        )
        assertEquals(
            "proforma_PF-20261021-0001.xml",
            InvoiceXml.fileName(data.copy(documentType = DocumentType.PROFORMA, number = "PF-20261021-0001")),
        )
        assertEquals(
            "export_E-20261021-0001.xml",
            InvoiceXml.fileName(data.copy(documentType = DocumentType.EXPORT, number = "E-20261021-0001")),
        )
    }

    @Test
    fun `a credit note carries the invoice it replaces`() {
        val creditNote = data.copy(
            documentType = DocumentType.AVOIR,
            number = "A-20261021-0001",
            referenceNumber = "F-20261021-0042",
        )

        val xml = InvoiceXml.build(creditNote, shop)

        assertTrue(xml.contains("type=\"avoir\""))
        assertTrue(
            xml.contains("<ReferenceFacture>Annule et remplace la facture N° F-20261021-0042</ReferenceFacture>"),
        )
        assertTrue(xml.contains("ARRÊTÉ LE PRÉSENT AVOIR"))
    }

    @Test
    fun `an export carries its incoterm`() {
        val export = data.copy(
            documentType = DocumentType.EXPORT,
            number = "E-20261021-0001",
            incoterm = "FOB Casablanca",
        )

        val xml = InvoiceXml.build(export, shop)

        assertTrue(xml.contains("type=\"export\""))
        assertTrue(xml.contains("<Incoterms>FOB Casablanca</Incoterms>"))
    }

    @Test
    fun `every special character is escaped`() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", InvoiceXml.escape("&<>\"'"))
    }
}
