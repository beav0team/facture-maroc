package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class DocumentStatusTest {

    private val today = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).parse("10/11/2026")!!

    private fun record(
        type: DocumentType = DocumentType.FACTURE,
        status: InvoiceStatus = InvoiceStatus.ISSUED,
        dueDate: String? = null,
        paid: Double = 0.0,
        paidAt: String = "",
    ) = InvoiceRecord(
        id = "r1",
        data = InvoiceData(
            number = "F-20261110-0001",
            date = "10/11/2026",
            client = InvoiceClient(name = "Karim"),
            lines = listOf(InvoiceLine("Chaise", 1.0, 1_000.0)),
            documentType = type,
            dueDate = dueDate,
            payments = if (paid > 0.0) {
                listOf(PaymentInfo(paidAmount = paid, paidAt = paidAt))
            } else {
                emptyList()
            },
        ),
        status = status,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `a draft says only that it is a draft`() {
        val line = DocumentStatus.of(record(status = InvoiceStatus.DRAFT), today)
        assertEquals("Brouillon", line.text)
        assertEquals(StatusTone.DRAFT, line.tone)
    }

    @Test
    fun `a credit note carries no status, its amount is already red and negative`() {
        val line = DocumentStatus.of(record(type = DocumentType.AVOIR), today)
        assertEquals("", line.text)
        assertEquals(StatusTone.NEUTRAL, line.tone)
    }

    @Test
    fun `a late invoice counts the days past the deadline, never on it`() {
        // Deadline 4 November, asked on the 10th: six whole days late.
        val late = DocumentStatus.of(record(dueDate = "04/11/2026"), today)
        assertEquals("6 j de retard", late.text)
        assertEquals(StatusTone.OVERDUE, late.tone)
    }

    @Test
    fun `the deadline day itself is not late`() {
        val due = DocumentStatus.of(record(dueDate = "10/11/2026"), today)
        assertEquals("Échéance 10/11", due.text)
        assertEquals(StatusTone.NEUTRAL, due.tone)
    }

    @Test
    fun `a partial payment shows what is left, not a rate`() {
        val partial = DocumentStatus.of(record(paid = 500.0), today)
        // 1 000 HT at 20 % is 1 200 TTC; 500 received leaves 700 to collect.
        assertEquals("700,00 DH dû", partial.text)
        assertEquals(StatusTone.DUE, partial.tone)
    }

    @Test
    fun `a settled document names the day it was paid`() {
        val paid = DocumentStatus.of(record(paid = 1_200.0, paidAt = "25/10/2026"), today)
        assertEquals("Payée le 25/10", paid.text)
        assertEquals(StatusTone.PAID, paid.tone)
    }

    @Test
    fun `a paid document without a recorded day still says it is paid`() {
        val paid = DocumentStatus.of(record(paid = 1_200.0), today)
        assertEquals("Payée", paid.text)
    }

    @Test
    fun `an unpaid invoice with no deadline says so plainly`() {
        val unpaid = DocumentStatus.of(record(), today)
        assertEquals("Impayée", unpaid.text)
        assertEquals(StatusTone.NEUTRAL, unpaid.tone)
    }

    @Test
    fun `a late document outranks a partial payment`() {
        val line = DocumentStatus.of(record(dueDate = "01/11/2026", paid = 500.0), today)
        assertEquals(StatusTone.OVERDUE, line.tone)
        assertEquals("9 j de retard", line.text)
    }

    @Test
    fun `a quote is not an unpaid invoice`() {
        // Nothing was sold yet: "Impayée" would chase a customer who never
        // bought anything.
        val quote = DocumentStatus.of(
            record(type = DocumentType.DEVIS).let {
                it.copy(data = it.data.copy(validityDate = "17/11/2026"))
            },
            today,
        )
        assertEquals("Devis · valable 17/11", quote.text)
        assertEquals(StatusTone.NEUTRAL, quote.tone)
    }

    @Test
    fun `a quote with no validity still says what it is`() {
        val quote = DocumentStatus.of(record(type = DocumentType.DEVIS), today)
        assertEquals("Devis", quote.text)
    }
}
