package com.yassine.facturemaroc.invoice

import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceLine
import com.yassine.facturemaroc.data.PaymentMethod
import com.yassine.facturemaroc.data.ShopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What the file has to say for a clearance platform to accept it.
 *
 * These assert facts about UBL 2.1, not about this implementation: the root
 * element per document type, the code lists, and the totals a receiver
 * cross-checks against the lines. A test that only pinned this writer's own
 * choices would keep passing while the file became unreadable to everyone else.
 */
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

    /** 100 exempt + 200 taxed, 10 % off: HT 270, base 180, TVA 36, TTC 306. */
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

    private val xml: String by lazy { InvoiceXml.build(data, shop) }

    @Test
    fun `an invoice is a UBL 2_1 Invoice in the standard namespaces`() {
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(xml.contains("<Invoice xmlns=\"${InvoiceXml.INVOICE_NAMESPACE}\""))
        assertTrue(xml.contains("xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""))
        assertTrue(xml.contains("xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\""))
        assertTrue(xml.contains("<cbc:UBLVersionID>2.1</cbc:UBLVersionID>"))
        assertTrue(xml.trimEnd().endsWith("</Invoice>"))
    }

    @Test
    fun `identity, date and currency are where a reader looks for them`() {
        assertTrue(xml.contains("<cbc:ID>F-20261021-0001</cbc:ID>"))
        assertTrue(xml.contains("<cbc:IssueDate>2026-10-21</cbc:IssueDate>"))
        assertTrue(xml.contains("<cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>"))
        assertTrue(xml.contains("<cbc:DocumentCurrencyCode>MAD</cbc:DocumentCurrencyCode>"))
    }

    @Test
    fun `the totals a receiver re-adds are the ones the paper shows`() {
        assertTrue(xml.contains("<cbc:LineExtensionAmount currencyID=\"MAD\">300.00</cbc:LineExtensionAmount>"))
        assertTrue(xml.contains("<cbc:TaxExclusiveAmount currencyID=\"MAD\">270.00</cbc:TaxExclusiveAmount>"))
        assertTrue(xml.contains("<cbc:TaxInclusiveAmount currencyID=\"MAD\">306.00</cbc:TaxInclusiveAmount>"))
        assertTrue(xml.contains("<cbc:AllowanceTotalAmount currencyID=\"MAD\">30.00</cbc:AllowanceTotalAmount>"))
        assertTrue(xml.contains("<cbc:PayableAmount currencyID=\"MAD\">306.00</cbc:PayableAmount>"))
        assertFalse(xml.contains("306,00"))
        assertFalse(xml.contains(",00</cbc:"))
    }

    @Test
    fun `tax is split into one subtotal per treatment, not averaged`() {
        assertTrue(xml.contains("<cbc:TaxAmount currencyID=\"MAD\">36.00</cbc:TaxAmount>"))
        // Taxed part: 200 less its share of the discount = 180, at 20 %.
        assertTrue(xml.contains("<cbc:TaxableAmount currencyID=\"MAD\">180.00</cbc:TaxableAmount>"))
        // Exempt part: 270 - 180 = 90, taxed at nothing.
        assertTrue(xml.contains("<cbc:TaxableAmount currencyID=\"MAD\">90.00</cbc:TaxableAmount>"))
        assertTrue(xml.contains("<cbc:Percent>20.00</cbc:Percent>"))
        assertTrue(xml.contains("<cbc:Percent>0.00</cbc:Percent>"))
    }

    @Test
    fun `a percentage carries no currency attribute`() {
        // cbc:Percent is a PercentType: it has no attributes, and a currency on
        // it is a schema violation that rejects the whole document.
        assertFalse(xml.contains("<cbc:Percent currencyID"))
        assertFalse(xml.contains("<cbc:MultiplierFactorNumeric currencyID"))
    }

    @Test
    fun `an ampersand or an angle bracket cannot break the document`() {
        assertTrue(xml.contains("Client &quot;Export&quot; &amp; Fils"))
        assertTrue(xml.contains("Atelier A&amp;B"))
        assertTrue(xml.contains("12 rue &lt;des Fleurs&gt;"))
        assertFalse(xml.contains("Client \"Export\""))
    }

    @Test
    fun `a line states its quantity, its unit and its tax treatment`() {
        assertTrue(xml.contains("<cbc:InvoicedQuantity unitCode=\"C62\">1</cbc:InvoicedQuantity>"))
        assertTrue(xml.contains("<cbc:InvoicedQuantity unitCode=\"C62\">2</cbc:InvoicedQuantity>"))
        assertTrue(xml.contains("<cbc:Name>Conseil</cbc:Name>"))
        assertTrue(xml.contains("<cac:ClassifiedTaxCategory>"))
        // The exempt line is category E, the taxed one S with the invoice rate.
        assertTrue(xml.contains("<cbc:ID>E</cbc:ID>"))
        assertTrue(xml.contains("<cbc:ID>S</cbc:ID>"))
        assertTrue(xml.contains("<cbc:PriceAmount currencyID=\"MAD\">100.00</cbc:PriceAmount>"))
    }

    @Test
    fun `units map to the UN ECE recommendation 20 codes`() {
        assertEquals("HUR", InvoiceXml.unitCode("heure"))
        assertEquals("DAY", InvoiceXml.unitCode("jour"))
        assertEquals("KGM", InvoiceXml.unitCode("kg"))
        assertEquals("LTR", InvoiceXml.unitCode("litre"))
        assertEquals("MTR", InvoiceXml.unitCode("m"))
        assertEquals("MTK", InvoiceXml.unitCode("m²"))
        assertEquals("C62", InvoiceXml.unitCode("unité"))
        // An unknown unit must not invent a code: "one" is the only safe claim.
        assertEquals("C62", InvoiceXml.unitCode("carton de 12"))
    }

    @Test
    fun `payment means are written for an invoice and carry the right code`() {
        assertTrue(xml.contains("<cbc:PaymentMeansCode>10</cbc:PaymentMeansCode>"))

        val transfer = InvoiceXml.build(data.copy(paymentMethod = PaymentMethod.TRANSFER), shop)
        assertTrue(transfer.contains("<cbc:PaymentMeansCode>30</cbc:PaymentMeansCode>"))
    }

    @Test
    fun `a credit note is a CreditNote, not an invoice with a different code`() {
        val creditNote = data.copy(
            documentType = DocumentType.AVOIR,
            number = "A-20261021-0001",
            referenceNumber = "F-20261021-0042",
        )

        val avoir = InvoiceXml.build(creditNote, shop)

        assertTrue(avoir.contains("<CreditNote xmlns=\"${InvoiceXml.CREDIT_NOTE_NAMESPACE}\""))
        assertTrue(avoir.trimEnd().endsWith("</CreditNote>"))
        assertTrue(avoir.contains("<cbc:CreditNoteTypeCode>381</cbc:CreditNoteTypeCode>"))
        // An invoice type code on a credit note is a contradiction a validator
        // would not catch but a receiver would act on.
        assertFalse(avoir.contains("cbc:InvoiceTypeCode"))
        assertTrue(avoir.contains("<cac:CreditNoteLine>"))
        assertTrue(avoir.contains("<cbc:CreditedQuantity"))
        assertFalse(avoir.contains("cbc:InvoicedQuantity"))
        assertTrue(avoir.contains("<cbc:ID>F-20261021-0042</cbc:ID>"))
        assertTrue(avoir.contains("<cac:InvoiceDocumentReference>"))
        assertTrue(avoir.contains("ARRÊTÉ LE PRÉSENT AVOIR"))
    }

    @Test
    fun `a quote is a proforma and never an amount owed`() {
        val quote = data.copy(
            documentType = DocumentType.DEVIS,
            number = "D-20261021-0001",
            validityDate = "31/12/2026",
        )

        val devis = InvoiceXml.build(quote, shop)

        assertTrue(devis.contains("<cbc:InvoiceTypeCode>386</cbc:InvoiceTypeCode>"))
        assertTrue(devis.contains("<cbc:DueDate>2026-12-31</cbc:DueDate>"))
        // A quote has not been paid, so it must not claim how it was.
        assertFalse(devis.contains("cac:PaymentMeans"))
    }

    @Test
    fun `a zero rate is declared exempt rather than taxed at zero`() {
        val autoEntrepreneur = data.copy(vatNotApplicable = true)

        val exempt = InvoiceXml.build(autoEntrepreneur, shop)

        assertTrue(exempt.contains("<cbc:TaxAmount currencyID=\"MAD\">0.00</cbc:TaxAmount>"))
        assertFalse(exempt.contains("<cbc:ID>S</cbc:ID>"))
        assertTrue(exempt.contains("<cbc:TaxableAmount currencyID=\"MAD\">270.00</cbc:TaxableAmount>"))
    }

    @Test
    fun `the amount in words travels with the file`() {
        assertTrue(xml.contains("TROIS CENT SIX DIRHAMS"))
        assertTrue(xml.contains("ARRÊTÉE LA PRÉSENTE FACTURE"))
    }

    @Test
    fun `every document type has a code and a file name a shop can find`() {
        assertEquals("380", InvoiceXml.typeCode(DocumentType.FACTURE))
        assertEquals("380", InvoiceXml.typeCode(DocumentType.SIMPLIFIEE))
        assertEquals("381", InvoiceXml.typeCode(DocumentType.AVOIR))
        assertEquals("384", InvoiceXml.typeCode(DocumentType.REGULARISATION))
        assertEquals("386", InvoiceXml.typeCode(DocumentType.DEVIS))
        assertEquals("386", InvoiceXml.typeCode(DocumentType.BON_DE_COMMANDE))
        assertEquals("386", InvoiceXml.typeCode(DocumentType.PROFORMA))

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
    fun `every special character is escaped`() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", InvoiceXml.escape("&<>\"'"))
    }

    @Test
    fun `the output is well formed XML, not just the right substrings`() {
        // The writer builds strings by hand, so "contains" proves nothing about
        // the document being parseable. A real parser catches the one bug that
        // matters most: an unclosed or misnested tag.
        val document = parse(xml)

        assertEquals("Invoice", document.documentElement.localName)
        assertEquals(InvoiceXml.INVOICE_NAMESPACE, document.documentElement.namespaceURI)
        assertEquals(2, document.getElementsByTagNameNS(CAC, "InvoiceLine").length)
        assertEquals(1, document.getElementsByTagNameNS(CAC, "LegalMonetaryTotal").length)
        assertEquals(
            "306.00",
            document.getElementsByTagNameNS(CBC, "PayableAmount").item(0).textContent,
        )
    }

    @Test
    fun `escaping survives a round trip through a parser`() {
        val document = parse(xml)
        // cbc:Name is a leaf, so its textContent is the name and not the
        // indentation the writer puts around it. Supplier first, customer
        // second, exactly as on the paper; the line items follow.
        val names = document.getElementsByTagNameNS(CBC, "Name")

        assertEquals("Atelier A&B", names.item(0).textContent)
        assertEquals("Client \"Export\" & Fils", names.item(1).textContent)
    }

    @Test
    fun `a percentage is an attribute-free decimal in a parsed document`() {
        val document = parse(xml)
        val percent = document.getElementsByTagNameNS(CBC, "Percent").item(0) as Element

        assertEquals(0, percent.attributes.length)
        assertEquals("20.00", percent.textContent)
    }

    @Test
    fun `a credit note parses as a CreditNote document`() {
        val avoir = InvoiceXml.build(
            data.copy(documentType = DocumentType.AVOIR, referenceNumber = "F-20261021-0042"),
            shop,
        )

        val document = parse(avoir)

        assertEquals("CreditNote", document.documentElement.localName)
        assertEquals(InvoiceXml.CREDIT_NOTE_NAMESPACE, document.documentElement.namespaceURI)
        assertEquals(2, document.getElementsByTagNameNS(CAC, "CreditNoteLine").length)
        assertEquals(0, document.getElementsByTagNameNS(CAC, "InvoiceLine").length)
    }

    private fun parse(text: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder()
            .parse(text.toByteArray(Charsets.UTF_8).inputStream())
    }

    private companion object {
        const val CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
        const val CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
    }
}
