package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class PartySummaryTest {

    private val today = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).parse("10/11/2026")!!

    private fun invoice(
        number: String,
        client: String,
        total: Double = 1_000.0,
        date: String = "10/11/2026",
        type: DocumentType = DocumentType.FACTURE,
        dueDate: String? = null,
        paid: Double = 0.0,
        draft: Boolean = false,
    ) = InvoiceRecord(
        id = number,
        data = InvoiceData(
            number = number,
            date = date,
            client = InvoiceClient(name = client),
            lines = listOf(InvoiceLine("Chaise", 1.0, total)),
            documentType = type,
            dueDate = dueDate,
            payments = if (paid > 0.0) listOf(PaymentInfo(paidAmount = paid)) else emptyList(),
        ),
        status = if (draft) InvoiceStatus.DRAFT else InvoiceStatus.ISSUED,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun client(name: String, role: PartyRole = PartyRole.CLIENT) =
        InvoiceClient(name = name, role = role)

    @Test
    fun `a client who owes nothing is not reported as owing`() {
        val summaries = emptyList<InvoiceRecord>().partySummaries(
            listOf(client("Karim")),
            emptyList(),
            today,
        )
        val summary = summaries[partyKey(PartyRole.CLIENT, "Karim")]!!
        assertEquals(0.0, summary.due, 0.001)
        assertEquals(0, summary.documents)
        assertTrue(!summary.owes && !summary.isLate)
    }

    @Test
    fun `a client's debt is net of his credit notes`() {
        val records = listOf(
            invoice("F-1", "Karim", total = 1_000.0),
            invoice("A-1", "Karim", total = 200.0, type = DocumentType.AVOIR),
        )
        val summary = records.partySummaries(listOf(client("Karim")), emptyList(), today)
            .getValue(partyKey(PartyRole.CLIENT, "Karim"))

        // 1 200 TTC billed, 240 TTC credited.
        assertEquals(960.0, summary.due, 0.001)
        assertEquals(2, summary.documents)
        assertTrue(summary.owes)
    }

    @Test
    fun `late money is split from the rest`() {
        val records = listOf(
            invoice("F-1", "Karim", dueDate = "01/11/2026"),
            invoice("F-2", "Karim", dueDate = "31/12/2026"),
        )
        val summary = records.partySummaries(listOf(client("Karim")), emptyList(), today)
            .getValue(partyKey(PartyRole.CLIENT, "Karim"))

        assertEquals(2_400.0, summary.due, 0.001)
        assertEquals(1_200.0, summary.overdue, 0.001)
        assertTrue(summary.isLate)
    }

    @Test
    fun `a client is matched whatever the casing and spacing`() {
        val records = listOf(invoice("F-1", "  karim  "))
        val summary = records.partySummaries(listOf(client("Karim")), emptyList(), today)
            .getValue(partyKey(PartyRole.CLIENT, "Karim"))
        assertTrue(summary.owes)
    }

    @Test
    fun `a draft is not a document given to anybody`() {
        val records = listOf(invoice("F-1", "Karim", draft = true))
        val summary = records.partySummaries(listOf(client("Karim")), emptyList(), today)
            .getValue(partyKey(PartyRole.CLIENT, "Karim"))
        assertEquals(0, summary.documents)
        assertEquals(0.0, summary.due, 0.001)
    }

    @Test
    fun `a supplier counts his receipts and never a balance`() {
        val purchases = listOf(
            Purchase(
                id = "p1",
                number = "BR-1",
                date = "02/11/2026",
                supplierName = "Bois du Nord",
                lines = listOf(PurchaseLine("c1", "Planche", 10, 50.0)),
            ),
        )
        val records = listOf(
            InvoiceRecord(
                id = "bc1",
                data = InvoiceData(
                    number = "BC-1",
                    date = "01/11/2026",
                    client = InvoiceClient(name = "Bois du Nord"),
                    lines = listOf(InvoiceLine("Planche", 10.0, 50.0)),
                    documentType = DocumentType.BON_DE_COMMANDE,
                    supplier = InvoiceClient(name = "Bois du Nord", role = PartyRole.SUPPLIER),
                ),
                status = InvoiceStatus.ISSUED,
                createdAt = 0,
                updatedAt = 0,
            ),
        )

        val summary = records.partySummaries(
            listOf(client("Bois du Nord", PartyRole.SUPPLIER)),
            purchases,
            today,
        ).getValue(partyKey(PartyRole.SUPPLIER, "Bois du Nord"))

        assertEquals(500.0, summary.purchasedHt, 0.001)
        assertEquals(1, summary.receipts)
        assertEquals(2, summary.documents)
        assertEquals(0.0, summary.due, 0.001)
        assertEquals("02/11/2026", summary.lastActivity)
    }
}
