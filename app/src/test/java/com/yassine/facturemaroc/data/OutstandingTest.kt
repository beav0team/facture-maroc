package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class OutstandingTest {

    private val today = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).parse("10/11/2026")!!

    private fun record(
        id: String,
        type: DocumentType = DocumentType.FACTURE,
        status: InvoiceStatus = InvoiceStatus.ISSUED,
        dueDate: String? = null,
        paid: Double = 0.0,
        amount: Double = 1_000.0,
        client: String = "Karim",
    ) = InvoiceRecord(
        id = id,
        data = InvoiceData(
            number = "F-$id",
            date = "10/11/2026",
            client = InvoiceClient(name = client),
            lines = listOf(InvoiceLine("Chaise", 1.0, amount)),
            documentType = type,
            dueDate = dueDate,
            payments = if (paid > 0.0) listOf(PaymentInfo(paidAmount = paid)) else emptyList(),
        ),
        status = status,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `an empty history owes nothing`() {
        val result = emptyList<InvoiceRecord>().outstanding(today)
        assertEquals(0.0, result.due, 0.001)
        assertEquals(0.0, result.overdue, 0.001)
        assertEquals(0, result.documents)
    }

    @Test
    fun `unpaid invoices add up, at TTC`() {
        val result = listOf(record("1"), record("2", client = "Yasmine")).outstanding(today)
        // 1 000 HT at 20 % each.
        assertEquals(2_400.0, result.due, 0.001)
        assertEquals(2, result.documents)
    }

    @Test
    fun `a partial payment only removes what was paid`() {
        val result = listOf(record("1", paid = 500.0)).outstanding(today)
        assertEquals(700.0, result.due, 0.001)
        assertEquals(1, result.documents)
    }

    @Test
    fun `late money is named separately`() {
        val result = listOf(
            record("1", dueDate = "01/11/2026"),
            record("2", dueDate = "31/12/2026"),
        ).outstanding(today)
        assertEquals(2_400.0, result.due, 0.001)
        assertEquals(1_200.0, result.overdue, 0.001)
    }

    @Test
    fun `a draft is not money owed to anyone`() {
        val result = listOf(record("1", status = InvoiceStatus.DRAFT)).outstanding(today)
        assertEquals(0.0, result.due, 0.001)
        assertEquals(0, result.documents)
    }

    @Test
    fun `a paid invoice leaves the count`() {
        val result = listOf(record("1", paid = 1_200.0)).outstanding(today)
        assertEquals(0.0, result.due, 0.001)
        assertEquals(0, result.documents)
    }

    @Test
    fun `a credit note reduces what the client owes`() {
        val result = listOf(
            record("1"),
            record("2", type = DocumentType.AVOIR, amount = 200.0),
        ).outstanding(today)
        // 1 200 billed, 240 given back.
        assertEquals(960.0, result.due, 0.001)
        assertEquals(1, result.documents)
    }

    @Test
    fun `a quote is not a debt`() {
        val result = listOf(record("1", type = DocumentType.DEVIS)).outstanding(today)
        assertEquals(0.0, result.due, 0.001)
    }
}
